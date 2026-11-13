package me.tamkungz.jlayer.modern;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Equalizer;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.Obuffer;
import javazoom.jl.decoder.OutputChannels;
import javazoom.jl.decoder.SampleBuffer;

/**
 * Modern, easy-to-use MP3 decoder.
 * <p>
 * Usage examples:
 * <pre>{@code
 * // Decode all frames and collect samples
 * try (Mp3Decoder decoder = Mp3Decoder.fromPath(Paths.get("song.mp3"))) {
 *     decoder.forEach(frame -> {
 *         short[] samples = frame.getSamplesCopy();
 *         // process...
 *     });
 * }
 *
 * // Convert to WAV
 * try (Mp3Decoder decoder = Mp3Decoder.fromPath(Paths.get("song.mp3"))) {
 *     decoder.writeWav(Paths.get("song.wav"));
 * }
 *
 * // Read info and tags without decoding
 * try (Mp3Decoder decoder = Mp3Decoder.fromPath(Paths.get("song.mp3"))) {
 *     System.out.println(decoder.getInfo());
 *     System.out.println(decoder.getTags());
 * }
 * }</pre>
 *
 * <p>A decoder is single-pass: {@link #iterator()}, {@link #stream()},
 * {@link #forEach(Consumer)}, {@link #decodeAsync(Consumer)}, {@link #decodeAll()},
 * the PCM and WAV methods, and the pull methods {@link #decodeNextFrame()},
 * {@link #decodeUpTo(int)} and {@link #skipFrames(int)} all share one decode pass.
 * Always close the decoder, ideally with try-with-resources.</p>
 *
 * @since 1.0
 */
public final class Mp3Decoder implements Iterable<Mp3Frame>, AutoCloseable {

    private final Bitstream bitstream;
    private final Path sourcePath;
    private final Object iterationLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final Decoder.Params params;
    private final Obuffer builderOutputBuffer;
    private final Decoder.DecodeEventListener builderEventListener;

    private volatile Mp3Info cachedInfo;
    private volatile Mp3Tags cachedTags;
    private boolean iterationStarted;
    private Mp3FrameIterator pullIterator;

    private Mp3Decoder(InputStream in, Path sourcePath, Builder builder) {
        Objects.requireNonNull(in, "InputStream must not be null");
        InputStream buffered = in.markSupported() ? in : new BufferedInputStream(in);

        this.bitstream = new Bitstream(buffered);
        this.sourcePath = sourcePath;
        this.params = builder.params;
        this.builderOutputBuffer = builder.outputBuffer;
        this.builderEventListener = builder.eventListener;
    }

    /**
     * Creates a decoder from an InputStream.
     * @param in the input stream (will be closed when decoder is closed)
     */
    public static Mp3Decoder fromStream(InputStream in) {
        return builder().build(in);
    }

    /**
     * Creates a decoder from a file path.
     * @param path path to MP3 file
     * @throws IOException if file cannot be opened
     */
    public static Mp3Decoder fromPath(Path path) throws IOException {
        return builder().build(path);
    }

    /**
     * Creates a decoder from a file name.
     * @param fileName name of MP3 file
     * @throws IOException if file cannot be opened
     */
    public static Mp3Decoder fromFile(String fileName) throws IOException {
        return fromPath(Path.of(fileName));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for configuring and creating Mp3Decoder instances.
     * <p>
     * Example usage:
     * <pre>{@code
     * Mp3Decoder decoder = Mp3Decoder.builder()
     *     .outputChannels(OutputChannels.LEFT)
     *     .equalizer(Equalizer.POP)
     *     .build(Paths.get("song.mp3"));
     * }</pre>
     */
    public static final class Builder {
        private final Decoder.Params params = new Decoder.Params();
        private Obuffer outputBuffer;
        private Decoder.DecodeEventListener eventListener;

        private Builder() {}

        /** Set output channels (default = BOTH). */
        public Builder outputChannels(OutputChannels channels) {
            params.setOutputChannels(channels);
            return this;
        }

        /** Set initial equalizer. */
        public Builder equalizer(Equalizer eq) {
            params.setInitialEqualizerSettings(eq);
            return this;
        }

        /**
         * Provide a custom output buffer (advanced). It must be a {@link SampleBuffer}, and
         * its contents are overwritten by every decoded frame; frames hold snapshots.
         *
         * @throws IllegalArgumentException if {@code buffer} is not a {@code SampleBuffer}
         */
        public Builder outputBuffer(Obuffer buffer) {
            if (buffer != null && !(buffer instanceof SampleBuffer)) {
                throw new IllegalArgumentException("outputBuffer must be a SampleBuffer");
            }
            this.outputBuffer = buffer;
            return this;
        }

        /** Register a decode event listener. */
        public Builder eventListener(Decoder.DecodeEventListener listener) {
            this.eventListener = listener;
            return this;
        }

        /** Build decoder from InputStream. */
        public Mp3Decoder build(InputStream in) {
            return new Mp3Decoder(in, null, this);
        }

        /** Build decoder from Path. */
        public Mp3Decoder build(Path path) throws IOException {
            return new Mp3Decoder(Files.newInputStream(path), path, this);
        }
    }

    /**
     * Returns metadata about the MP3 stream (reads the first frame).
     * <p>
     * For path-backed decoders this never affects the decode pass. For stream-backed
     * decoders it must be called before iteration starts.
     * </p>
     *
     * @throws Mp3Exception if stream is invalid or cannot be read
     * @throws IllegalStateException if the decoder is closed, or is stream-backed and
     *         iteration has already started
     */
    public Mp3Info getInfo() {
        ensureOpen();
        Mp3Info info = cachedInfo;
        if (info != null) {
            return info;
        }

        if (sourcePath != null) {
            try {
                info = Mp3Info.read(sourcePath);
            } catch (IOException e) {
                throw new Mp3Exception("Failed to read stream info", e);
            }
        } else {
            synchronized (iterationLock) {
                if (iterationStarted) {
                    throw new IllegalStateException(
                            "getInfo() on stream-backed decoders must be called before iteration starts");
                }
                try {
                    Header header = bitstream.readFrame();
                    if (header == null) {
                        throw new Mp3Exception("Empty stream - no frame found");
                    }
                    info = new Mp3Info(header, -1);
                    bitstream.unreadFrame();
                } catch (BitstreamException e) {
                    throw new Mp3Exception("Failed to read stream info", e);
                }
            }
        }
        cachedInfo = info;
        return info;
    }

    /**
     * Returns the ID3 tags of the source file.
     * <p>
     * Only path-backed decoders can read tags; for stream-backed decoders this returns
     * {@link Mp3Tags#EMPTY}. Use {@link Mp3Tags#read(InputStream)} on a separate stream
     * if the tags of a stream are needed.
     * </p>
     *
     * @throws Mp3Exception if the file cannot be read
     */
    public Mp3Tags getTags() {
        ensureOpen();
        Mp3Tags tags = cachedTags;
        if (tags == null) {
            if (sourcePath == null) {
                return Mp3Tags.EMPTY;
            }
            try {
                tags = Mp3Tags.read(sourcePath);
            } catch (IOException e) {
                throw new Mp3Exception("Failed to read tags", e);
            }
            cachedTags = tags;
        }
        return tags;
    }

    @Override
    public Iterator<Mp3Frame> iterator() {
        ensureOpen();
        synchronized (iterationLock) {
            if (iterationStarted) {
                throw new IllegalStateException("Mp3Decoder is single-pass: create a new instance for a second iteration");
            }
            iterationStarted = true;
            return newFrameIterator();
        }
    }

    /**
     * Returns a sequential {@code Stream} of decoded frames.
     * Closing the stream closes this decoder.
     */
    public Stream<Mp3Frame> stream() {
        ensureOpen();
        Iterator<Mp3Frame> lazy = new Iterator<>() {
            private Iterator<Mp3Frame> it;

            private Iterator<Mp3Frame> delegate() {
                if (it == null) {
                    it = iterator();
                }
                return it;
            }

            @Override public boolean hasNext() { return delegate().hasNext(); }
            @Override public Mp3Frame next() { return delegate().next(); }
        };

        Spliterator<Mp3Frame> spliterator = Spliterators.spliteratorUnknownSize(
            lazy, Spliterator.ORDERED | Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false).onClose(this::close);
    }

    /**
     * Decodes and returns exactly one next frame from the stream.
     *
     * @return next decoded frame, or {@code null} if end-of-stream is reached
     * @throws IllegalStateException if another iteration API already owns the decode pass
     * @since 1.1.3
     */
    public Mp3Frame decodeNextFrame() {
        ensureOpen();
        synchronized (iterationLock) {
            Mp3FrameIterator it = pullIterator();
            return it.hasNext() ? it.next() : null;
        }
    }

    /**
     * Decodes up to {@code maxFrames} frames and returns them.
     *
     * @param maxFrames maximum number of frames to decode, must be &gt; 0
     * @return decoded frames (possibly empty if end-of-stream has already been reached)
     * @throws IllegalArgumentException if {@code maxFrames <= 0}
     * @since 1.1.3
     */
    public List<Mp3Frame> decodeUpTo(int maxFrames) {
        if (maxFrames <= 0) {
            throw new IllegalArgumentException("maxFrames must be > 0");
        }

        List<Mp3Frame> out = new ArrayList<>(Math.min(maxFrames, 64));
        for (int i = 0; i < maxFrames; i++) {
            Mp3Frame frame = decodeNextFrame();
            if (frame == null) {
                break;
            }
            out.add(frame);
        }
        return out;
    }

    /**
     * Skips up to {@code count} frames without decoding them, which is much faster than
     * decoding and discarding them. Use it to seek forward by a frame count.
     * <p>
     * Skipped frames do not feed the decoder, so the first frames decoded afterwards can
     * contain a short audible glitch when they depend on the bit reservoir of the skipped
     * frames.
     * </p>
     *
     * @param count number of frames to skip, must be &gt;= 0
     * @return the number of frames actually skipped (less than {@code count} at end-of-stream)
     * @throws IllegalArgumentException if {@code count < 0}
     * @throws IllegalStateException if another iteration API already owns the decode pass
     * @since 1.2.0
     */
    public int skipFrames(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        ensureOpen();
        synchronized (iterationLock) {
            Mp3FrameIterator it = pullIterator();
            int skipped = 0;
            while (skipped < count && it.skip()) {
                skipped++;
            }
            return skipped;
        }
    }

    /**
     * Processes each decoded frame with the given consumer.
     * This method blocks until all frames are processed.
     */
    @Override
    public void forEach(Consumer<? super Mp3Frame> action) {
        Objects.requireNonNull(action);
        iterator().forEachRemaining(action);
    }

    /**
     * Decodes all frames asynchronously, feeding them to the given consumer.
     * @param consumer frame consumer
     * @return a CompletableFuture that completes when decoding finishes or fails
     */
    public CompletableFuture<Void> decodeAsync(Consumer<? super Mp3Frame> consumer) {
        return decodeAsync(consumer, ForkJoinPool.commonPool());
    }

    /**
     * Decodes all frames asynchronously using a custom executor.
     */
    public CompletableFuture<Void> decodeAsync(Consumer<? super Mp3Frame> consumer, Executor executor) {
        Objects.requireNonNull(consumer);
        ensureOpen();
        return CompletableFuture.runAsync(() -> iterator().forEachRemaining(consumer), executor);
    }

    /**
     * Decodes all frames and returns them as a list.
     * <p><b>Warning:</b> This may consume large amounts of memory for long files.</p>
     */
    public List<Mp3Frame> decodeAll() {
        List<Mp3Frame> list = new ArrayList<>();
        forEach(list::add);
        return list;
    }

    /**
     * Decodes all frames and concatenates the PCM data into a single byte array
     * in little-endian format.
     * @return concatenated 16-bit PCM bytes
     */
    public byte[] decodeAllPcmLittleEndian() {
        return decodeAllPcm(true);
    }

    /**
     * Decodes all frames and concatenates the PCM data into a single byte array
     * in big-endian format.
     */
    public byte[] decodeAllPcmBigEndian() {
        return decodeAllPcm(false);
    }

    private byte[] decodeAllPcm(boolean littleEndian) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        forEach(frame -> out.writeBytes(littleEndian
                ? frame.toByteArrayLittleEndian()
                : frame.toByteArrayBigEndian()));
        return out.toByteArray();
    }

    /**
     * Decodes the remaining frames and writes them as a 16-bit PCM WAV file. The file is
     * written incrementally, so memory use does not grow with the length of the song. A
     * partially written file is deleted if decoding fails.
     *
     * @param target the WAV file to create or overwrite
     * @throws Mp3Exception if decoding or writing fails, or there are no frames
     * @since 1.2.0
     */
    public void writeWav(Path target) {
        Objects.requireNonNull(target, "target must not be null");
        Iterator<Mp3Frame> frames = iterator();
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.allocate(WavWriter.HEADER_SIZE));
            long dataLength = 0;
            int sampleRate = 0;
            int channels = 0;
            while (frames.hasNext()) {
                Mp3Frame frame = frames.next();
                sampleRate = frame.getSampleRate();
                channels = frame.getChannels();
                ByteBuffer pcm = ByteBuffer.wrap(frame.toByteArrayLittleEndian());
                dataLength += pcm.remaining();
                while (pcm.hasRemaining()) {
                    channel.write(pcm);
                }
            }
            if (sampleRate == 0) {
                throw new Mp3Exception("No frames to write");
            }
            channel.write(ByteBuffer.wrap(WavWriter.header(sampleRate, channels, dataLength)), 0);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            if (e instanceof Mp3Exception mp3) {
                throw mp3;
            }
            throw new Mp3Exception("Failed to write WAV file", e);
        }
    }

    /**
     * Decodes the remaining frames and writes them as a 16-bit PCM WAV stream. The PCM data
     * is buffered in memory because the WAV header needs the total length; prefer
     * {@link #writeWav(Path)} for long songs. The stream is not closed.
     *
     * @param out the destination
     * @throws Mp3Exception if decoding or writing fails, or there are no frames
     * @since 1.2.0
     */
    public void writeWav(OutputStream out) {
        Objects.requireNonNull(out, "out must not be null");
        int[] format = new int[2];
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        forEach(frame -> {
            format[0] = frame.getSampleRate();
            format[1] = frame.getChannels();
            pcm.writeBytes(frame.toByteArrayLittleEndian());
        });
        if (format[0] == 0) {
            throw new Mp3Exception("No frames to write");
        }
        try {
            out.write(WavWriter.header(format[0], format[1], pcm.size()));
            pcm.writeTo(out);
        } catch (IOException e) {
            throw new Mp3Exception("Failed to write WAV data", e);
        }
    }

    /**
     * Closes the decoder and releases the underlying input stream.
     * Idempotent.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
                // nothing more can be released
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Decoder is closed");
        }
    }

    private Mp3FrameIterator pullIterator() {
        if (!iterationStarted) {
            iterationStarted = true;
            pullIterator = newFrameIterator();
        }
        if (pullIterator == null) {
            throw new IllegalStateException(
                    "Mp3Decoder single-pass already consumed by another iteration API");
        }
        return pullIterator;
    }

    private Mp3FrameIterator newFrameIterator() {
        Decoder decoder = new Decoder(params);
        if (builderOutputBuffer != null) {
            decoder.setOutputBuffer(builderOutputBuffer);
        }
        if (builderEventListener != null) {
            decoder.setEventListener(builderEventListener);
        }
        return new Mp3FrameIterator(bitstream, decoder);
    }
}
