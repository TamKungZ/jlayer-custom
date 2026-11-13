package me.tamkungz.jlayer.modern.player;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;
import me.tamkungz.jlayer.modern.Mp3Info;

/**
 * A modern, thread-safe wrapper around the legacy
 * {@link javazoom.jl.player.advanced.AdvancedPlayer} that plays a range of frames.
 * <p>
 * Unlike {@link Mp3Player} it has no pause or state model; use it when only a frame range
 * has to be played and playback events are enough.
 * </p>
 */
public final class AdvancedPlayer implements AutoCloseable {

    /** How far the first frame may be from the start of a non-path stream (ID3 tag included). */
    private static final int MAX_PROBE_BYTES = 8 * 1024 * 1024;

    private final javazoom.jl.player.advanced.AdvancedPlayer player;
    private final InputStream inputStream;
    private final Thread playThread;
    private final Mp3Info info;
    private final int startFrame;
    private final int endFrame;

    private volatile boolean stopped;
    private volatile boolean closed;
    private volatile Throwable lastError;

    private AdvancedPlayer(Builder builder, InputStream stream, Mp3Info info, AudioDevice device)
            throws JavaLayerException {
        this.inputStream = stream;
        this.info = info;
        this.player = new javazoom.jl.player.advanced.AdvancedPlayer(nonClosing(stream), device);
        if (builder.listener != null) {
            player.setPlayBackListener(builder.listener.toLegacyListener());
        }
        this.startFrame = builder.startFrame;
        this.endFrame = builder.endFrame;
        this.playThread = new Thread(this::runPlayback, "ModernAdvancedPlayer");
        this.playThread.setDaemon(builder.daemon);
    }

    /** The legacy player closes its stream on stop; ownership stays with {@link #close()}. */
    private static InputStream nonClosing(InputStream delegate) {
        return new FilterInputStream(delegate) {
            @Override public void close() { /* closed by AdvancedPlayer.close() */ }
        };
    }

    public static Builder fromPath(Path path) throws IOException {
        Files.size(path);
        return new Builder(path, null);
    }

    public static Builder fromFile(String fileName) throws IOException {
        return fromPath(Path.of(fileName));
    }

    public static Builder fromStream(InputStream inputStream) {
        return new Builder(null, Objects.requireNonNull(inputStream, "inputStream must not be null"));
    }

    public static final class Builder {
        private final Path path;
        private final InputStream inputStream;
        private AudioDevice audioDevice;
        private ModernPlaybackListener listener;
        private int startFrame = 0;
        private int endFrame = Integer.MAX_VALUE;
        private boolean daemon = true;

        private Builder(Path path, InputStream inputStream) {
            this.path = path;
            this.inputStream = inputStream;
        }

        public Builder audioDevice(AudioDevice device) {
            this.audioDevice = device;
            return this;
        }

        public Builder listener(ModernPlaybackListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder legacyListener(PlaybackListener legacyListener) {
            this.listener = event -> {
                if (event.getId() == PlaybackEvent.STARTED) {
                    legacyListener.playbackStarted(event);
                } else if (event.getId() == PlaybackEvent.STOPPED) {
                    legacyListener.playbackFinished(event);
                }
            };
            return this;
        }

        public Builder fromFrame(int startFrame) {
            if (startFrame < 0) throw new IllegalArgumentException("startFrame must be >= 0");
            this.startFrame = startFrame;
            return this;
        }

        public Builder toFrame(int endFrame) {
            if (endFrame < 0) throw new IllegalArgumentException("endFrame must be >= 0");
            this.endFrame = endFrame;
            return this;
        }

        public Builder all() {
            this.startFrame = 0;
            this.endFrame = Integer.MAX_VALUE;
            return this;
        }

        public Builder daemon(boolean daemon) {
            this.daemon = daemon;
            return this;
        }

        /**
         * Builds the player. The source is opened and probed here; if that fails it is closed again.
         *
         * @throws IOException if the source cannot be read or has no MPEG frame, or the
         *         audio device cannot be created
         */
        public AdvancedPlayer build() throws IOException {
            InputStream stream = null;
            try {
                Mp3Info info;
                if (path != null) {
                    info = Mp3Info.read(path);
                    stream = Files.newInputStream(path);
                } else {
                    stream = inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);
                    stream.mark(MAX_PROBE_BYTES);
                    try {
                        info = Mp3Info.probe(stream, -1);
                    } finally {
                        stream.reset();
                    }
                }
                AudioDevice device = audioDevice != null
                        ? audioDevice
                        : FactoryRegistry.systemRegistry().createAudioDevice();
                return new AdvancedPlayer(this, stream, info, device);
            } catch (JavaLayerException e) {
                closeQuietly(stream, e);
                throw new IOException("Failed to create player", e);
            } catch (IOException | RuntimeException e) {
                closeQuietly(stream, e);
                throw e;
            }
        }

        private static void closeQuietly(InputStream stream, Exception failure) {
            if (stream == null) {
                return;
            }
            try {
                stream.close();
            } catch (IOException suppressed) {
                failure.addSuppressed(suppressed);
            }
        }
    }

    /** Starts playback on a background thread. Calling it again has no effect. */
    public synchronized void start() {
        ensureNotClosed();
        if (!playThread.isAlive()) {
            playThread.start();
        }
    }

    /**
     * Plays to the end and waits for it.
     *
     * @throws AdvancedPlayerException if playback failed
     */
    public void playAndWait() {
        start();
        try {
            playThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop();
            return;
        }
        Throwable error = lastError;
        if (error != null) {
            throw new AdvancedPlayerException("Playback error", error);
        }
    }

    public CompletableFuture<Void> playAsync(Executor executor) {
        return CompletableFuture.runAsync(this::playAndWait, executor);
    }

    public CompletableFuture<Void> playAsync() {
        return playAsync(ForkJoinPool.commonPool());
    }

    /**
     * Waits for the playback thread to end.
     *
     * @return {@code true} if it ended; {@code false} if the timeout elapsed
     * @since 1.2.0
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        playThread.join(Math.max(1, unit.toMillis(timeout)));
        return !playThread.isAlive();
    }

    public synchronized void stop() {
        if (!stopped && !closed) {
            stopped = true;
            try {
                player.stop();
            } catch (NullPointerException ignored) {
                // The legacy AdvancedPlayer.stop() reads the device position, which is null
                // when the play thread already finished (e.g. NullAudioDevice completes instantly).
            }
        }
    }

    public boolean isPlaying() {
        return playThread.isAlive() && !stopped && !closed;
    }

    public Mp3Info getInfo() {
        return info;
    }

    /** Returns the error that ended playback, if any. */
    public Throwable getLastError() {
        return lastError;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        stop();
        closed = true;
        try {
            if (playThread.isAlive()) {
                playThread.join(5000);
                if (playThread.isAlive()) {
                    playThread.interrupt();
                    playThread.join(1000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            playThread.interrupt();
        }

        // Close the stream only after the playback thread can no longer read from it.
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // nothing more can be released
        }
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("Player is closed");
        }
    }

    private void runPlayback() {
        try {
            player.play(startFrame, endFrame);
        } catch (JavaLayerException | RuntimeException e) {
            if (!closed && !stopped) {
                lastError = e;
            }
        }
    }
}
