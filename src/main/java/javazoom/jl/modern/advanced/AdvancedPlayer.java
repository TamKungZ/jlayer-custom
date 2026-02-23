package javazoom.jl.modern.advanced;

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

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.modern.ModernPlaybackListener;
import javazoom.jl.modern.Mp3Info;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

/**
 * A modern, thread-safe, and easy-to-use wrapper around {@link AdvancedPlayer}
 * from the JLayer library.
 */
public final class AdvancedPlayer implements AutoCloseable {

    private final javazoom.jl.player.advanced.AdvancedPlayer player; // fully qualified to avoid confusion
    private final InputStream inputStream;
    private final Thread playThread;
    private final Mp3Info info;
    private final int startFrame;
    private final int endFrame;

    private volatile boolean stopped = false;
    private volatile boolean closed = false;

    private AdvancedPlayer(Builder builder) throws IOException, JavaLayerException {
        // Determine stream size if path is known
        long streamSize = -1;
        if (builder.inputStreamPath != null) {
            streamSize = Files.size(builder.inputStreamPath);
        }

        // Ensure mark is supported for probing the first frame.
        InputStream is = builder.inputStream;
        if (!is.markSupported()) {
            is = new BufferedInputStream(is);
        }

        // Read first frame to obtain Mp3Info.
        // IMPORTANT: Bitstream wraps the stream in a PushbackInputStream and, when
        // closed, closes that wrapper – which in turn closes the underlying stream.
        // To prevent that we read bytes manually, probe the header, then reset.
        is.mark(4096); // generous mark limit; MP3 frames are at most ~1441 bytes
        try {
            // Wrap in a non-closing proxy so Bitstream.close() cannot close `is`.
            InputStream probeStream = nonClosingWrapper(is);
            Bitstream tempBitstream = new Bitstream(probeStream);
            Header firstHeader = tempBitstream.readFrame();
            if (firstHeader == null) {
                throw new IOException("No MPEG frames found in stream");
            }
            this.info = new Mp3Info(firstHeader, streamSize);
            // Do NOT call tempBitstream.close() – the non-closing wrapper ensures
            // the underlying stream stays open, and we reset it below.
        } finally {
            is.reset(); // rewind to the very start
        }

        // Create the actual AdvancedPlayer from the reset stream.
        this.inputStream = is; // now owned by us
        AudioDevice device = builder.audioDevice != null
                ? builder.audioDevice
                : FactoryRegistry.systemRegistry().createAudioDevice();
        // Pass a non-closing wrapper to the legacy player so that its internal
        // Bitstream/close() does not close our underlying stream unexpectedly.
        InputStream playerStream = nonClosingWrapper(inputStream);
        this.player = new javazoom.jl.player.advanced.AdvancedPlayer(playerStream, device);
        if (builder.listener != null) {
            player.setPlayBackListener(builder.listener.toLegacyListener());
        }

        this.startFrame = builder.startFrame;
        this.endFrame = builder.endFrame;
        this.playThread = new Thread(this::runPlayback, "ModernAdvancedPlayer");
        this.playThread.setDaemon(builder.daemon);
    }

    /**
     * Returns a wrapper around {@code delegate} whose {@link InputStream#close()}
     * method is a no-op.  All other operations are delegated transparently.
     */
    private static InputStream nonClosingWrapper(InputStream delegate) {
        return new FilterInputStream(delegate) {
            @Override public void close() { /* intentionally empty */ }
        };
    }

    // ---------- Builder ----------

    public static Builder fromPath(Path path) throws IOException {
        return new Builder(Files.newInputStream(path), path);
    }

    public static Builder fromFile(String fileName) throws IOException {
        return fromPath(Path.of(fileName));
    }

    public static Builder fromStream(InputStream inputStream) {
        return new Builder(inputStream, null);
    }

    public static final class Builder {
        private final InputStream inputStream;
        private final Path inputStreamPath; // optional, for size info
        private AudioDevice audioDevice;
        private ModernPlaybackListener listener;
        private int startFrame = 0;
        private int endFrame = Integer.MAX_VALUE;
        private boolean daemon = true;

        private Builder(InputStream inputStream, Path path) {
            this.inputStream = Objects.requireNonNull(inputStream, "inputStream must not be null");
            this.inputStreamPath = path;
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

        public AdvancedPlayer build() throws IOException {
            try {
                return new javazoom.jl.modern.advanced.AdvancedPlayer(this);
            } catch (JavaLayerException e) {
                throw new IOException("Failed to create player", e);
            }
        }
    }

    // ---------- Playback Control ----------

    public synchronized void start() {
        ensureNotClosed();
        if (!playThread.isAlive()) {
            playThread.start();
        }
    }

    public void playAndWait() {
        start();
        try {
            playThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop();
        }
    }

    public CompletableFuture<Void> playAsync(Executor executor) {
        return CompletableFuture.runAsync(this::playAndWait, executor);
    }

    public CompletableFuture<Void> playAsync() {
        return playAsync(ForkJoinPool.commonPool());
    }

    public synchronized void stop() {
        if (!stopped && !closed) {
            stopped = true;
            try {
                player.stop(); // closes internal device and bitstream
            } catch (NullPointerException ignored) {
                // The legacy AdvancedPlayer.stop() calls dev.getPosition() via createEvent(),
                // but dev is null if the play thread finished before stop() was invoked
                // (e.g. NullAudioDevice processes audio at CPU speed and may complete instantly).
            }
        }
    }

    public boolean isPlaying() {
        return playThread.isAlive() && !stopped && !closed;
    }

    public Mp3Info getInfo() {
        return info;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            stop();
            try {
                if (playThread.isAlive()) {
                    // Wait longer for the playback thread to finish.
                    // NullAudioDevice processes instantly, but we need to ensure
                    // the thread has fully exited before closing the stream.
                    playThread.join(5000);
                    
                    // If still alive after 5 seconds, interrupt it
                    if (playThread.isAlive()) {
                        playThread.interrupt();
                        playThread.join(1000); // Give it 1 more second after interrupt
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Try to interrupt the playback thread before closing
                playThread.interrupt();
            }
            
            // Only close the stream after ensuring the playback thread has terminated
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ---------- Internal ----------

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("Player is closed");
        }
    }

    private void runPlayback() {
        try {
            player.play(startFrame, endFrame);
        } catch (JavaLayerException e) {
            // Check if this is due to stream being closed during shutdown
            if (closed || stopped) {
                // Expected during normal shutdown, don't throw
                return;
            }
            throw new AdvancedPlayerException("Playback error", e);
        }
    }
}
