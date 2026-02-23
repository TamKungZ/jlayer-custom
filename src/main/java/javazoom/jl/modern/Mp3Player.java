package javazoom.jl.modern;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Equalizer;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.OutputChannels;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.JavaSoundAudioDevice;

/**
 * Modern, easy‑to‑use MP3 player with playback control.
 * <p>
 * Usage example:
 * <pre>{@code
 * try (Mp3Player player = Mp3Player.fromPath(Paths.get("song.mp3")).build()) {
 *     player.play(); // non‑blocking, plays in background
 *     Thread.sleep(5000);
 *     player.pause();
 *     Thread.sleep(2000);
 *     player.resume();
 *     // ...
 *     player.stop(); // or close()
 * }
 * }</pre>
 *
 * <p>The player runs decoding in a separate thread. All playback methods are thread‑safe.</p>
 *
 * @since 1.0
 */
public final class Mp3Player implements AutoCloseable {

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
    private volatile Thread playbackThread;
    private final Path sourcePath;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final Object pauseLock = new Object();
    private volatile State state = State.STOPPED;
    private volatile Listener listener;
    private volatile Throwable lastError;
    private volatile Mp3Info cachedInfo;
    private volatile boolean playbackStarted;
    private volatile boolean playbackFinished;

    // Builder
    private Mp3Player(Builder builder) throws IOException {
        this.decoder = builder.decoderBuilder.build(builder.inputStream);
        this.audioDevice = builder.audioDevice;
        this.sourcePath = builder.sourcePath;
        createPlaybackThread(builder.daemon);
    }

    private void createPlaybackThread(boolean daemon) {
        Thread t = new Thread(this::runPlayback, "Mp3Player");
        t.setDaemon(daemon);
        this.playbackThread = t;
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
        private final Path sourcePath;
        private final Mp3Decoder.Builder decoderBuilder = Mp3Decoder.builder();
        private AudioDevice audioDevice;
        private boolean daemon = true;
        private Listener listener;

        private Builder(InputStream inputStream, Path sourcePath) {
            this.inputStream = Objects.requireNonNull(inputStream, "inputStream must not be null");
            this.sourcePath = sourcePath;
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

        public Mp3Player build() throws IOException {
            if (audioDevice == null) {
                try {
                    audioDevice = FactoryRegistry.systemRegistry().createAudioDevice();
                } catch (JavaLayerException e) {
                    throw new IOException("Failed to create default audio device", e);
                }
            }
            Mp3Player player = new Mp3Player(this);
            if (listener != null) {
                player.setListener(listener);
            }
            return player;
        }
    }

    // ---------- Playback Control ----------

    /** Starts or resumes playback (non‑blocking). */
    public void play() {
        synchronized (pauseLock) {
            if (state == State.CLOSED) {
                throw new IllegalStateException("Player is closed");
            }
            if (state == State.PLAYING) {
                return;
            }
            if (state == State.STOPPED) {
                if (playbackFinished || (playbackStarted && !playbackThread.isAlive())) {
                    throw new IllegalStateException("Mp3Player is single-use after stop/completion; create a new player instance");
                }
                stopRequested.set(false);
                playbackStarted = true;
                playbackThread.start();
                changeState(State.PLAYING);
            } else if (state == State.PAUSED) {
                resume();
            }
        }
    }

    /** Pauses playback. */
    public void pause() {
        if (state == State.PLAYING) {
            changeState(State.PAUSED);
        }
    }

    /** Resumes playback after pause. */
    public void resume() {
        if (state == State.PAUSED) {
            changeState(State.PLAYING);
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }
    }

    /**
     * Stops playback.
     * <p>
     * This player is single-use: after stop/completion, playback cannot be restarted
     * on the same instance. Create a new {@code Mp3Player} to play again.
     * </p>
     */
    public void stop() {
        synchronized (pauseLock) {
            if (state == State.PLAYING || state == State.PAUSED) {
                stopRequested.set(true);
                if (state == State.PAUSED) {
                    pauseLock.notifyAll();
                }
                try {
                    playbackThread.join(1000);
                    if (playbackThread.isAlive()) {
                        playbackThread.interrupt();
                        playbackThread.join(1000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                changeState(State.STOPPED);
            }
        }
    }

    /** Closes the player and releases resources. Cannot be used again. */
    @Override
    public void close() {
        synchronized (pauseLock) {
            if (state != State.CLOSED) {
                stop(); // ensures thread ends
                try {
                    decoder.close();
                } catch (Exception e) {
                    // ignore
                }
                if (audioDevice != null) {
                    audioDevice.close();
                }
                changeState(State.CLOSED);
            }
        }
    }

    /** Returns current playback state. */
    public State getState() {
        return state;
    }

    /** Returns current playback position in milliseconds, or 0 if not playing. */
    public int getPosition() {
        if (audioDevice != null && audioDevice.isOpen()) {
            return audioDevice.getPosition();
        }
        return 0;
    }

    /** Returns metadata about the MP3 stream (blocks until first frame is read). */
    public Mp3Info getInfo() {
        if (cachedInfo != null) {
            return cachedInfo;
        }

        if (sourcePath != null) {
            synchronized (this) {
                if (cachedInfo == null) {
                    try (Mp3Decoder infoDecoder = Mp3Decoder.fromPath(sourcePath)) {
                        cachedInfo = infoDecoder.getInfo();
                    } catch (IOException e) {
                        throw new Mp3PlayerException("Failed to probe MP3 info", e);
                    }
                }
            }
            return cachedInfo;
        }

        return decoder.getInfo();
    }

    /** Sets a listener for playback events. May be null. */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Returns the last error that occurred during playback, if any. */
    public Throwable getLastError() {
        return lastError;
    }

    // ---------- Internal Playback Loop ----------

    private void runPlayback() {
        try {
            for (Mp3Frame frame : decoder) {
                while (state == State.PAUSED && !stopRequested.get()) {
                    synchronized (pauseLock) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                if (stopRequested.get()) {
                    break;
                }
                if (state != State.PLAYING) {
                    // Should not happen, but safety
                    continue;
                }
                ensureAudioDeviceOpen(frame);
                // Write samples to audio device
                short[] samples = frame.getSamples();
                audioDevice.write(samples, 0, frame.getSampleCount());
                if (listener != null) {
                    listener.onFramePlayed(frame);
                }
            }
            // End of stream
            if (audioDevice.isOpen()) {
                audioDevice.flush();
            }
            if (!stopRequested.get() && listener != null) {
                listener.onPlaybackFinished();
            }
        } catch (Exception e) {
            lastError = e;
            if (listener != null) {
                listener.onError(e);
            }
        } catch (Error e) {
            throw e;
        } finally {
            playbackFinished = true;
            // Ensure device is closed if not already
            if (audioDevice != null && audioDevice.isOpen()) {
                audioDevice.close();
            }
            if (state != State.CLOSED) {
                changeState(State.STOPPED);
            }
        }
    }

    private void ensureAudioDeviceOpen(Mp3Frame frame) {
        if (audioDevice.isOpen()) {
            return;
        }
        try {
            if (audioDevice instanceof JavaSoundAudioDevice jsAudioDevice) {
                AudioFormat format = new AudioFormat(frame.getSampleRate(), 16, frame.getChannels(), true, false);
                jsAudioDevice.open(format);
            } else {
                audioDevice.open(createFormatAwareDecoder(frame));
            }
        } catch (JavaLayerException e) {
            throw new Mp3PlayerException("Failed to open audio device", e);
        }
    }

    private Decoder createFormatAwareDecoder(Mp3Frame frame) {
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

    private void changeState(State newState) {
        State old = state;
        state = newState;
        if (listener != null) {
            listener.onStateChanged(old, newState);
        }
    }

    // ---------- Listener Interface ----------

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

    // ---------- More ----------

    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    public void playAndWait() {
        play();
        try {
            playbackThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Mp3PlayerException("Interrupted while waiting for playback", e);
        }
        if (lastError != null) {
            if (lastError instanceof RuntimeException re) {
                throw re;
            }
            throw new Mp3PlayerException("Playback failed", lastError);
        }
    }

    public CompletableFuture<Void> playAsync() {
        return CompletableFuture.runAsync(this::playAndWait, ForkJoinPool.commonPool());
    }

    public CompletableFuture<Void> playAsync(Executor executor) {
        return CompletableFuture.runAsync(this::playAndWait, executor);
    }
}
