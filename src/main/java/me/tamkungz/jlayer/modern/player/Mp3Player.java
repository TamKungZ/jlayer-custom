package me.tamkungz.jlayer.modern.player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Equalizer;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.OutputChannels;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.JavaSoundAudioDevice;
import me.tamkungz.jlayer.modern.Mp3Decoder;
import me.tamkungz.jlayer.modern.Mp3Frame;
import me.tamkungz.jlayer.modern.Mp3Info;
import me.tamkungz.jlayer.modern.Mp3Tags;

/**
 * Modern, easy-to-use MP3 player with playback control.
 * <p>
 * Usage example:
 * <pre>{@code
 * try (Mp3Player player = Mp3Player.fromPath(Paths.get("song.mp3")).build()) {
 *     player.play(); // non-blocking, plays in background
 *     Thread.sleep(5000);
 *     player.pause();
 *     Thread.sleep(2000);
 *     player.resume();
 *     player.awaitTermination(1, TimeUnit.MINUTES);
 * }
 * }</pre>
 *
 * <p>The player decodes on a separate thread. All playback methods are thread-safe.
 * A player is single-use: after {@link #stop()} or completion it cannot be restarted.
 * Exceptions thrown by a {@link Listener} are logged and do not interrupt playback.</p>
 *
 * @since 1.0
 */
public final class Mp3Player implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(Mp3Player.class.getName());
    private static final long JOIN_TIMEOUT_MS = 1000;

    public enum State {
        /** Not started or stopped. */
        STOPPED,
        /** Playing. */
        PLAYING,
        /** Paused. */
        PAUSED,
        /** Playback finished or closed. */
        CLOSED
    }

    private final Mp3Decoder decoder;
    private final AudioDevice audioDevice;
    private final Thread playbackThread;

    // guards state transitions, stopRequested and started; pausing waits on it
    private final Object lock = new Object();
    private boolean stopRequested;
    private boolean started;

    private volatile State state = State.STOPPED;
    private volatile Listener listener;
    private volatile Throwable lastError;

    private Mp3Player(Mp3Decoder decoder, AudioDevice audioDevice, boolean daemon, Listener listener) {
        this.decoder = decoder;
        this.audioDevice = audioDevice;
        this.listener = listener;
        this.playbackThread = new Thread(this::runPlayback, "Mp3Player");
        this.playbackThread.setDaemon(daemon);
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
        private final Mp3Decoder.Builder decoderBuilder = Mp3Decoder.builder();
        private AudioDevice audioDevice;
        private boolean daemon = true;
        private Listener listener;

        private Builder(Path path, InputStream inputStream) {
            this.path = path;
            this.inputStream = inputStream;
        }

        /** Set output channels (e.g., stereo, mono). */
        public Builder outputChannels(OutputChannels channels) {
            decoderBuilder.outputChannels(channels);
            return this;
        }

        /** Set initial equalizer. */
        public Builder equalizer(Equalizer eq) {
            decoderBuilder.equalizer(eq);
            return this;
        }

        /**
         * Set a custom audio device. If not set, the default JavaSound device will be used.
         */
        public Builder audioDevice(AudioDevice device) {
            this.audioDevice = device;
            return this;
        }

        /** Whether the playback thread should be a daemon thread (default true). */
        public Builder daemon(boolean daemon) {
            this.daemon = daemon;
            return this;
        }

        /** Set a listener for playback events. */
        public Builder listener(Listener listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Builds the player. The source is opened here; if the build fails it is closed again.
         *
         * @throws IOException if the source or the default audio device cannot be opened
         */
        public Mp3Player build() throws IOException {
            Mp3Decoder decoder = path != null ? decoderBuilder.build(path) : decoderBuilder.build(inputStream);
            try {
                AudioDevice device = audioDevice != null ? audioDevice : createDefaultDevice();
                return new Mp3Player(decoder, device, daemon, listener);
            } catch (IOException | RuntimeException e) {
                decoder.close();
                throw e;
            }
        }

        private static AudioDevice createDefaultDevice() throws IOException {
            try {
                return FactoryRegistry.systemRegistry().createAudioDevice();
            } catch (JavaLayerException e) {
                throw new IOException("Failed to create default audio device", e);
            }
        }
    }

    /**
     * Starts or resumes playback (non-blocking).
     *
     * @throws IllegalStateException if the player is closed, or was already stopped or finished
     */
    public void play() {
        State previous;
        synchronized (lock) {
            switch (state) {
                case CLOSED -> throw new IllegalStateException("Player is closed");
                case PLAYING -> {
                    return;
                }
                case PAUSED -> {
                    previous = swapState(State.PLAYING);
                    lock.notifyAll();
                }
                default -> {
                    if (started) {
                        throw new IllegalStateException(
                                "Mp3Player is single-use after stop/completion; create a new player instance");
                    }
                    started = true;
                    stopRequested = false;
                    previous = swapState(State.PLAYING);
                    playbackThread.start();
                }
            }
        }
        fireStateChanged(previous, State.PLAYING);
    }

    /** Pauses playback. */
    public void pause() {
        State previous = null;
        synchronized (lock) {
            if (state == State.PLAYING) {
                previous = swapState(State.PAUSED);
            }
        }
        if (previous != null) {
            fireStateChanged(previous, State.PAUSED);
        }
    }

    /** Resumes playback after pause. */
    public void resume() {
        State previous = null;
        synchronized (lock) {
            if (state == State.PAUSED) {
                previous = swapState(State.PLAYING);
                lock.notifyAll();
            }
        }
        if (previous != null) {
            fireStateChanged(previous, State.PLAYING);
        }
    }

    /**
     * Stops playback and waits briefly for the playback thread to end.
     * <p>
     * This player is single-use: after stop/completion, playback cannot be restarted
     * on the same instance. Create a new {@code Mp3Player} to play again.
     * </p>
     */
    public void stop() {
        synchronized (lock) {
            if (state != State.PLAYING && state != State.PAUSED) {
                return;
            }
            stopRequested = true;
            lock.notifyAll();
        }
        awaitPlaybackThread();
        transitionTo(State.STOPPED);
    }

    /** Closes the player and releases resources. Cannot be used again. */
    @Override
    public void close() {
        stop();
        decoder.close();
        audioDevice.close();
        transitionTo(State.CLOSED);
    }

    /** Returns current playback state. */
    public State getState() {
        return state;
    }

    /** Returns current playback position in milliseconds, or 0 if not playing. */
    public int getPosition() {
        if (audioDevice.isOpen()) {
            return audioDevice.getPosition();
        }
        return 0;
    }

    /**
     * Returns metadata about the MP3 stream. For stream-backed players this must be
     * called before playback starts.
     */
    public Mp3Info getInfo() {
        return decoder.getInfo();
    }

    /** Returns the ID3 tags of the source file; empty for stream-backed players. */
    public Mp3Tags getTags() {
        return decoder.getTags();
    }

    /** Sets a listener for playback events. May be null. */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Returns the last error that occurred during playback, if any. */
    public Throwable getLastError() {
        return lastError;
    }

    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    /**
     * Waits for playback to end.
     *
     * @return {@code true} if playback has ended; {@code false} if the timeout elapsed
     *         or playback was never started
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @since 1.2.0
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        synchronized (lock) {
            if (!started) {
                return false;
            }
        }
        playbackThread.join(Math.max(1, unit.toMillis(timeout)));
        return !playbackThread.isAlive();
    }

    /**
     * Plays to the end and waits for it.
     *
     * @throws Mp3PlayerException if the wait is interrupted or playback failed
     */
    public void playAndWait() {
        play();
        try {
            playbackThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Mp3PlayerException("Interrupted while waiting for playback", e);
        }
        Throwable error = lastError;
        if (error != null) {
            if (error instanceof RuntimeException re) {
                throw re;
            }
            throw new Mp3PlayerException("Playback failed", error);
        }
    }

    public CompletableFuture<Void> playAsync() {
        return playAsync(ForkJoinPool.commonPool());
    }

    public CompletableFuture<Void> playAsync(Executor executor) {
        return CompletableFuture.runAsync(this::playAndWait, executor);
    }

    private void runPlayback() {
        try {
            for (Mp3Frame frame : decoder) {
                synchronized (lock) {
                    while (state == State.PAUSED && !stopRequested) {
                        lock.wait();
                    }
                    if (stopRequested) {
                        break;
                    }
                }
                ensureAudioDeviceOpen(frame);
                audioDevice.write(frame.getSamples(), 0, frame.getSampleCount());
                notifyListener(l -> l.onFramePlayed(frame));
            }
            if (audioDevice.isOpen()) {
                audioDevice.flush();
            }
            boolean stopped;
            synchronized (lock) {
                stopped = stopRequested;
            }
            if (!stopped) {
                notifyListener(Listener::onPlaybackFinished);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            lastError = e;
            notifyListener(l -> l.onError(e));
        } finally {
            if (audioDevice.isOpen()) {
                audioDevice.close();
            }
            transitionTo(State.STOPPED);
        }
    }

    private void awaitPlaybackThread() {
        if (Thread.currentThread() == playbackThread) {
            return;
        }
        try {
            playbackThread.join(JOIN_TIMEOUT_MS);
            if (playbackThread.isAlive()) {
                playbackThread.interrupt();
                playbackThread.join(JOIN_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureAudioDeviceOpen(Mp3Frame frame) {
        if (audioDevice.isOpen()) {
            return;
        }
        try {
            if (audioDevice instanceof JavaSoundAudioDevice jsAudioDevice) {
                jsAudioDevice.open(new AudioFormat(frame.getSampleRate(), 16, frame.getChannels(), true, false));
            } else {
                audioDevice.open(createFormatAwareDecoder(frame));
            }
        } catch (JavaLayerException e) {
            throw new Mp3PlayerException("Failed to open audio device", e);
        }
    }

    private static Decoder createFormatAwareDecoder(Mp3Frame frame) {
        final int sampleRate = frame.getSampleRate();
        final int channels = frame.getChannels();
        return new Decoder() {
            @Override
            public int getOutputFrequency() {
                return sampleRate;
            }

            @Override
            public int getOutputChannels() {
                return channels;
            }
        };
    }

    /** Must be called with {@code lock} held. Returns the previous state. */
    private State swapState(State next) {
        State previous = state;
        state = next;
        return previous;
    }

    /** Moves to {@code next} unless already there or closed, then notifies the listener. */
    private void transitionTo(State next) {
        State previous;
        synchronized (lock) {
            if (state == next || state == State.CLOSED) {
                return;
            }
            previous = swapState(next);
            lock.notifyAll();
        }
        fireStateChanged(previous, next);
    }

    private void fireStateChanged(State previous, State next) {
        notifyListener(l -> l.onStateChanged(previous, next));
    }

    private void notifyListener(Consumer<Listener> call) {
        Listener current = listener;
        if (current == null) {
            return;
        }
        try {
            call.accept(current);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Mp3Player listener threw an exception", e);
        }
    }

    /** Listener for playback events. All methods have default no-op implementations. */
    public interface Listener {
        /** Called when playback state changes. */
        default void onStateChanged(State oldState, State newState) {}

        /** Called after each frame is played. */
        default void onFramePlayed(Mp3Frame frame) {}

        /** Called when playback reaches the end of the stream. */
        default void onPlaybackFinished() {}

        /** Called when an error occurs during playback. */
        default void onError(Throwable error) {}
    }
}
