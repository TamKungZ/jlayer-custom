package javazoom.jl.modern;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Equalizer;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.Obuffer;
import javazoom.jl.decoder.OutputChannels;
import javazoom.jl.decoder.SampleBuffer;

/**
 * Modern, easy‑to‑use MP3 decoder.
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
 * // Async decoding
 * Mp3Decoder decoder = Mp3Decoder.fromPath(Paths.get("song.mp3"));
 * CompletableFuture<Void> future = decoder.decodeAsync(frame -> {
 *     byte[] pcm = frame.toByteArrayLittleEndian();
 *     // send to audio output...
 * });
 * future.join();
 *
 * // Get stream info without decoding all
 * try (Mp3Decoder decoder = Mp3Decoder.fromPath(Paths.get("song.mp3"))) {
 *     Mp3Info info = decoder.getInfo();
 *     System.out.println(info);
 * }
 * }</pre>
 *
 * <p>The decoder implements {@link AutoCloseable} – always use try‑with‑resources
 * to ensure the underlying stream is closed.</p>
 *
 * @since 1.0
 */
public final class Mp3Decoder implements Iterable<Mp3Frame>, AutoCloseable {

    private final Bitstream bitstream;
    private final long streamSize; // bytes, may be -1 if unknown
    private boolean closed = false;
    private boolean firstFrameRead = false;
    private Mp3Info cachedInfo = null;
    private long frameNumber = 0;
    private boolean infoQueried = false;

    private final Decoder.Params params;
    private final Obuffer builderOutputBuffer;
    private final Decoder.DecodeEventListener builderEventListener;

    // ---------- Constructors ----------

    private Mp3Decoder(InputStream in, Builder builder) {
        Objects.requireNonNull(in, "InputStream must not be null");
        this.bitstream = new Bitstream(in);
        this.params = builder.params;
        this.builderOutputBuffer = builder.outputBuffer;
        this.builderEventListener = builder.eventListener;
        this.streamSize = -1;
    }

    private Mp3Decoder(Path path, Builder builder) throws IOException {
        this(Files.newInputStream(path), builder);
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

    // ---------- Builder ----------

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

        /** Provide a custom output buffer (advanced). */
        public Builder outputBuffer(Obuffer buffer) {
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
            return new Mp3Decoder(in, this);
        }

        /** Build decoder from Path. */
        public Mp3Decoder build(Path path) throws IOException {
            return new Mp3Decoder(path, this);
        }
    }

    // ---------- Public API ----------

    /**
     * Returns metadata about the MP3 stream (reads the first frame).
     * @throws Mp3Exception if stream is invalid or cannot be read
     */
    public Mp3Info getInfo() {
        ensureOpen();
        infoQueried = true;
        if (cachedInfo == null) {
            // We need to read first frame without consuming it permanently.
            // We'll read, decode, then reset? But we cannot rewind the stream easily.
            // Instead, we'll read the first frame, decode it (to get header info),
            // and then "unread" the frame data via bitstream.unreadFrame().
            // However, unreadFrame() only works if we haven't called closeFrame().
            try {
                Header h = bitstream.readFrame();
                if (h == null) {
                    throw new Mp3Exception("Empty stream – no frame found");
                }

                // Decode the frame to populate the decoder's internal state, which may be needed for accurate info (e.g. VBR bitrate).
                cachedInfo = new Mp3Info(h, streamSize);

                // Push the frame bytes back so the stream can be iterated later.
                bitstream.unreadFrame();
            } catch (BitstreamException e) {
                throw new Mp3Exception("Failed to read stream info", e);
            }

        }

        return cachedInfo;
    }
    
    @Override
    public Iterator<Mp3Frame> iterator() {
        ensureOpen();
        if (infoQueried) {
            throw new RuntimeException("Mp3Decoder.getInfo() invalidates the decoder; create a new instance to iterate");
        }
        return new Mp3Iterator();
    }

    /**
     * Returns a sequential {@code Stream} of decoded frames.
     * The stream must be closed after use to release resources.
     */
    public Stream<Mp3Frame> stream() {
        ensureOpen();
        // Create a lazily-initialized iterator so the underlying
        // Bitstream and Decoder are not touched until the Stream is consumed.
        Iterator<Mp3Frame> lazy = new Iterator<>() {
            private Iterator<Mp3Frame> it;
            private void ensure() { if (it == null) it = iterator(); }
            @Override public boolean hasNext() { ensure(); return it.hasNext(); }
            @Override public Mp3Frame next() { ensure(); return it.next(); }
        };

        Spliterator<Mp3Frame> spliterator = Spliterators.spliteratorUnknownSize(
            lazy, Spliterator.ORDERED | Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false)
                .onClose(this::close);
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
        return CompletableFuture.runAsync(() -> {
            iterator().forEachRemaining(consumer);
        }, executor);
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
     * in little‑endian format.
     * @return concatenated 16‑bit PCM bytes
     */
    public byte[] decodeAllPcmLittleEndian() {
        return decodeAllPcm(true);
    }

    /**
     * Decodes all frames and concatenates the PCM data into a single byte array
     * in big‑endian format.
     */
    public byte[] decodeAllPcmBigEndian() {
        return decodeAllPcm(false);
    }

    /**
     * Decodes all frames and concatenates the PCM data into a single byte array.
     * @param littleEndian
     * @return
     */
    private byte[] decodeAllPcm(boolean littleEndian) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        forEach(frame -> {
            try {
                byte[] data = littleEndian ? frame.toByteArrayLittleEndian() : frame.toByteArrayBigEndian();
                baos.write(data);
            } catch (IOException e) {
                throw new Mp3Exception("Failed to write PCM", e);
            }
        });
        return baos.toByteArray();
    }

    /**
     * Closes the decoder and releases the underlying input stream.
     * Idempotent.
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                bitstream.close();
            } catch (BitstreamException e) {
                // ignore on close
            }
        }
    }

    // ---------- Internal ----------

    /**
     * Ensures the decoder is open. Throws an exception if closed. 
     * This is called at the start of all public methods to prevent usage after close.
    */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Decoder is closed");
        }
    }

    private class Mp3Iterator implements Iterator<Mp3Frame> {

        private final Decoder decoder;
        private Header nextHeader;
        private boolean eos = false;

        Mp3Iterator() {
            this.decoder = new Decoder(params);

            if (builderOutputBuffer != null) {
                decoder.setOutputBuffer(builderOutputBuffer);
            }
            if (builderEventListener != null) {
                decoder.setEventListener(builderEventListener);
            }

            advance();   // ← ต้องอยู่ใน class
        }

        private void advance() {
            if (eos) return;
            try {
                nextHeader = bitstream.readFrame();
                if (nextHeader == null) {
                    eos = true;
                }
            } catch (BitstreamException e) {
                throw new Mp3Exception("Failed to read frame", e);
            }
        }

        @Override
        public boolean hasNext() {
            return !eos;
        }

        @Override
        public Mp3Frame next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            Header h = nextHeader;
            int channels = (h.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;

            SampleBuffer buf = new SampleBuffer(h.frequency(), channels);
            decoder.setOutputBuffer(buf);

            try {
                decoder.decodeFrame(h, bitstream);
                bitstream.closeFrame();
                Mp3Frame frame = new Mp3Frame(h, buf, frameNumber++);
                advance();
                return frame;
            } catch (DecoderException e) {
                throw new Mp3Exception("Decoding error at frame " + frameNumber, e);
            }
        }
    }

}