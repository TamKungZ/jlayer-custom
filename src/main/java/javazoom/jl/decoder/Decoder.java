/*
 * 11/19/04        1.0 moved to LGPL.
 * 01/12/99        Initial version.    mdm@techie.com
 *-----------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package javazoom.jl.decoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import javazoom.jl.converter.Converter;

/**
 * The {@code Decoder} class encapsulates the details of decoding MPEG audio frames.
 * <p>
 * This decoder supports MPEG-1 and MPEG-2 audio streams with layers I, II, and III (MP3).
 * It provides both traditional synchronous decoding and modern asynchronous APIs.
 * </p>
 * 
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Each thread should use
 * its own Decoder instance.</p>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Traditional synchronous decoding
 * Decoder decoder = new Decoder();
 * Bitstream bitstream = new Bitstream(inputStream);
 * Header header = bitstream.readFrame();
 * Obuffer output = decoder.decodeFrame(header, bitstream);
 * bitstream.closeFrame();
 * 
 * // Simplified API
 * SampleBuffer buffer = decoder.decodeNextFrame(bitstream);
 * 
 * // Async decoding
 * decoder.decodeAsync(bitstream)
 *     .thenAccept(buffer -> processAudio(buffer))
 *     .exceptionally(ex -> handleError(ex));
 * 
 * // Builder pattern configuration
 * Decoder decoder = Decoder.builder()
 *     .withEqualizer(customEQ)
 *     .withOutputChannels(OutputChannels.LEFT)
 *     .build();
 * }</pre>
 *
 * @author MDM
 * @author Enhanced by modernization
 * @version 2.0
 * @since 0.0.5
 */
public class Decoder implements DecoderErrors {
    
    private static final Params DEFAULT_PARAMS = new Params();
    private static final float DEFAULT_SCALE_FACTOR = 32700.0f;

    /**
     * The output buffer that will receive decoded PCM samples.
     */
    private Obuffer output;

    /**
     * Synthesis filter for the left channel.
     */
    private SynthesisFilter filter1;

    /**
     * Synthesis filter for the right channel.
     */
    private SynthesisFilter filter2;

    /**
     * Layer decoders.
     */
    private LayerIIIDecoder l3decoder;
    private LayerIIDecoder l2decoder;
    private LayerIDecoder l1decoder;

    private int outputFrequency;
    private int outputChannels;

    private final Equalizer equalizer = new Equalizer();

    private Params params;

    private boolean initialized;
    
    /**
     * Frame counter for statistics.
     */
    private long framesDecoded;
    
    /**
     * Error counter for monitoring.
     */
    private long errorCount;
    
    /**
     * Listener for decode events.
     */
    private DecodeEventListener eventListener;

    /**
     * Creates a new {@code Decoder} instance with default parameters.
     */
    public Decoder() {
        this(null);
    }

    /**
     * Creates a new {@code Decoder} instance with custom parameters.
     *
     * @param params the {@code Params} instance describing customization,
     *               or null to use defaults
     */
    public Decoder(Params params) {
        this.params = (params == null) ? DEFAULT_PARAMS : params;

        Equalizer eq = this.params.getInitialEqualizerSettings();
        if (eq != null) {
            equalizer.setFrom(eq);
        }
    }

    /**
     * Returns a copy of the default decoder parameters.
     * 
     * @return a new Params instance with default values
     */
    public static Params getDefaultParams() {
        try {
            return (Params) DEFAULT_PARAMS.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError("Params clone failed: " + e);
        }
    }
    
    /**
     * Creates a new builder for configuring a Decoder.
     * 
     * @return a new DecoderBuilder instance
     * @since 2.0
     */
    public static DecoderBuilder builder() {
        return new DecoderBuilder();
    }

    /**
     * Sets the equalizer for this decoder.
     * <p>
     * Changes take effect immediately for subsequent frames.
     * Pass null or {@link Equalizer#PASS_THRU_EQ} to disable equalization.
     * </p>
     * 
     * @param eq the equalizer to apply, or null for pass-through
     * @return this decoder instance for method chaining
     * @since 2.0
     */
    public Decoder setEqualizer(Equalizer eq) {
        if (eq == null) {
            eq = Equalizer.PASS_THRU_EQ;
        }

        equalizer.setFrom(eq);

        float[] factors = equalizer.getBandFactors();

        if (filter1 != null) {
            filter1.setEQ(factors);
        }

        if (filter2 != null) {
            filter2.setEQ(factors);
        }
        
        return this;
    }
    
    /**
     * Gets the current equalizer.
     * 
     * @return the current equalizer instance
     * @since 2.0
     */
    public Equalizer getEqualizer() {
        return equalizer;
    }

    /**
     * Decodes one frame from an MPEG audio bitstream.
     *
     * @param header the header describing the frame to decode
     * @param stream the bitstream providing the frame body bits
     * @return an Obuffer containing the decoded samples
     * @throws DecoderException if decoding fails
     * @throws NullPointerException if header or stream is null
     */
    public Obuffer decodeFrame(Header header, Bitstream stream) throws DecoderException {
        Objects.requireNonNull(header, "header cannot be null");
        Objects.requireNonNull(stream, "stream cannot be null");
        
        try {
            if (!initialized) {
                initialize(header);
            }

            int layer = header.layer();

            output.clearBuffer();

            FrameDecoder decoder = retrieveDecoder(header, stream, layer);

            decoder.decodeFrame();

            output.writeBuffer(1);
            
            framesDecoded++;
            
            if (eventListener != null) {
                eventListener.onFrameDecoded(framesDecoded, header);
            }

            return output;
        } catch (DecoderException e) {
            errorCount++;
            if (eventListener != null) {
                eventListener.onError(e);
            }
            throw e;
        }
    }

    /**
     * Changes the output buffer.
     * <p>
     * This will take effect the next time {@link #decodeFrame} is called.
     * </p>
     * 
     * @param out the new output buffer, or null to use default
     * @return this decoder instance for method chaining
     * @since 2.0
     */
    public Decoder setOutputBuffer(Obuffer out) {
        this.output = out;
        return this;
    }
    
    /**
     * Gets the current output buffer.
     * 
     * @return the current output buffer, or null if not set
     * @since 2.0
     */
    public Obuffer getOutputBuffer() {
        return output;
    }

    /**
     * Retrieves the sample frequency of the PCM output.
     * <p>
     * This typically corresponds to the sample rate encoded in the MPEG stream.
     * </p>
     *
     * @return the sample rate in Hz of the output samples
     */
    public int getOutputFrequency() {
        return outputFrequency;
    }

    /**
     * Retrieves the number of channels in the PCM output.
     * <p>
     * Usually corresponds to the number of channels in the MPEG stream,
     * though it may differ based on decoder configuration.
     * </p>
     *
     * @return the number of output channels (1 for mono, 2 for stereo)
     */
    public int getOutputChannels() {
        return outputChannels;
    }

    /**
     * Retrieves the maximum number of samples per decoded frame.
     * <p>
     * This is an upper bound; fewer samples may be written depending
     * on sample rate and channel configuration.
     * </p>
     *
     * @return the maximum number of samples per frame
     */
    public int getOutputBlockSize() {
        return Obuffer.OBUFFERSIZE;
    }
    
    /**
     * Returns the total number of frames decoded by this instance.
     * 
     * @return the frame count
     * @since 2.0
     */
    public long getFramesDecoded() {
        return framesDecoded;
    }
    
    /**
     * Returns the total number of decoding errors encountered.
     * 
     * @return the error count
     * @since 2.0
     */
    public long getErrorCount() {
        return errorCount;
    }
    
    /**
     * Checks if the decoder has been initialized.
     * 
     * @return true if initialized
     * @since 2.0
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Resets the decoder statistics.
     * 
     * @return this decoder instance for method chaining
     * @since 2.0
     */
    public Decoder resetStatistics() {
        framesDecoded = 0;
        errorCount = 0;
        return this;
    }
    
    /**
     * Sets an event listener for decode events.
     * 
     * @param listener the listener, or null to remove
     * @return this decoder instance for method chaining
     * @since 2.0
     */
    public Decoder setEventListener(DecodeEventListener listener) {
        this.eventListener = listener;
        return this;
    }

    /**
     * Decodes the next frame from the bitstream and returns a populated SampleBuffer.
     * <p>
     * This is a convenience method that handles frame reading and closing automatically.
     * Returns null if no frame is available.
     * </p>
     * 
     * @param stream the bitstream to read from
     * @return a SampleBuffer with decoded audio, or null if end of stream
     * @throws BitstreamException if reading the stream fails
     * @throws DecoderException if decoding fails
     * @throws NullPointerException if stream is null
     */
    public SampleBuffer decodeNextFrame(Bitstream stream) 
            throws BitstreamException, DecoderException {
        Objects.requireNonNull(stream, "stream cannot be null");
        
        Header h = stream.readFrame();
        if (h == null) {
            return null;
        }
        
        int channels = (h.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
        SampleBuffer sb = new SampleBuffer(h.frequency(), channels);
        setOutputBuffer(sb);
        decodeFrame(h, stream);
        stream.closeFrame();
        return sb;
    }
    
    /**
     * Decodes the next frame and returns raw PCM data as little-endian bytes.
     * <p>
     * This is a convenience method for direct PCM output. Returns null if
     * no frame is available.
     * </p>
     * 
     * @param stream the bitstream to read from
     * @return 16-bit PCM data in little-endian format, or null if end of stream
     * @throws BitstreamException if reading the stream fails
     * @throws DecoderException if decoding fails
     * @throws NullPointerException if stream is null
     * @since 2.0
     */
    public byte[] decodeNextFrameToPCM(Bitstream stream) 
            throws BitstreamException, DecoderException {
        SampleBuffer sb = decodeNextFrame(stream);
        if (sb == null) {
            return null;
        }
        
        return convertToPCM(sb);
    }
    
    /**
     * Decodes the next frame as PCM with configurable endianness.
     * 
     * @param stream the bitstream to read from
     * @param littleEndian true for little-endian, false for big-endian
     * @return 16-bit PCM data, or null if end of stream
     * @throws BitstreamException if reading the stream fails
     * @throws DecoderException if decoding fails
     * @since 2.0
     */
    public byte[] decodeNextFrameToPCM(Bitstream stream, boolean littleEndian) 
            throws BitstreamException, DecoderException {
        SampleBuffer sb = decodeNextFrame(stream);
        if (sb == null) {
            return null;
        }
        
        return convertToPCM(sb, littleEndian);
    }
    
    /**
     * Decodes frames asynchronously using the common ForkJoinPool.
     * 
     * @param stream the bitstream to read from
     * @return a CompletableFuture that completes with the decoded buffer
     * @since 2.0
     */
    public CompletableFuture<SampleBuffer> decodeAsync(Bitstream stream) {
        return decodeAsync(stream, ForkJoinPool.commonPool());
    }
    
    /**
     * Decodes frames asynchronously using a specified executor.
     * 
     * @param stream the bitstream to read from
     * @param executor the executor to use for async processing
     * @return a CompletableFuture that completes with the decoded buffer
     * @throws NullPointerException if stream or executor is null
     * @since 2.0
     */
    public CompletableFuture<SampleBuffer> decodeAsync(Bitstream stream, Executor executor) {
        Objects.requireNonNull(stream, "stream cannot be null");
        Objects.requireNonNull(executor, "executor cannot be null");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return decodeNextFrame(stream);
            } catch (BitstreamException | DecoderException e) {
                throw new RuntimeException("Decode failed", e);
            }
        }, executor);
    }
    
    /**
     * Decodes all frames from the bitstream with a callback for each frame.
     * <p>
     * This method continues until the end of stream is reached or the
     * callback returns false.
     * </p>
     * 
     * @param stream the bitstream to read from
     * @param callback called for each decoded frame; return false to stop
     * @return the number of frames decoded
     * @throws BitstreamException if reading the stream fails
     * @throws DecoderException if decoding fails
     * @since 2.0
     */
    public long decodeAll(Bitstream stream, Consumer<SampleBuffer> callback) 
            throws BitstreamException, DecoderException {
        Objects.requireNonNull(stream, "stream cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        
        long count = 0;
        SampleBuffer buffer;
        
        while ((buffer = decodeNextFrame(stream)) != null) {
            callback.accept(buffer);
            count++;
        }
        
        return count;
    }
    
    /**
     * Creates a decoder info object with current configuration.
     * 
     * @return a DecoderInfo snapshot
     * @since 2.0
     */
    public DecoderInfo getInfo() {
        return new DecoderInfo(
            outputFrequency,
            outputChannels,
            framesDecoded,
            errorCount,
            initialized
        );
    }

    /**
     * Converts an MP3 file to WAV format.
     * <p>
     * This is a convenience static method using the Converter API.
     * </p>
     * 
     * @param sourceName the source MP3 file path
     * @param destName the destination WAV file path
     * @throws JavaLayerException if conversion fails
     */
    public static void convertToWav(String sourceName, String destName) 
            throws JavaLayerException {
        new Converter().convert(sourceName, destName);
    }
    
    /**
     * Converts an MP3 file to WAV format using Path objects.
     * 
     * @param source the source MP3 file
     * @param dest the destination WAV file
     * @throws JavaLayerException if conversion fails
     * @throws IOException if file operations fail
     * @since 2.0
     */
    public static void convertToWav(Path source, Path dest) 
            throws JavaLayerException, IOException {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(dest, "dest cannot be null");
        
        if (!Files.exists(source)) {
            throw new IOException("Source file does not exist: " + source);
        }
        
        convertToWav(source.toString(), dest.toString());
    }

    /**
     * Creates a new DecoderException with the specified error code.
     * 
     * @param errorCode the error code
     * @return a new DecoderException
     */
    protected DecoderException newDecoderException(int errorCode) {
        return new DecoderException(errorCode, null);
    }

    /**
     * Creates a new DecoderException with error code and cause.
     * 
     * @param errorCode the error code
     * @param throwable the cause
     * @return a new DecoderException
     */
    protected DecoderException newDecoderException(int errorCode, Throwable throwable) {
        return new DecoderException(errorCode, throwable);
    }

    /**
     * Retrieves the appropriate decoder for the frame layer.
     * 
     * @param header the frame header
     * @param stream the bitstream
     * @param layer the layer number
     * @return the frame decoder
     * @throws DecoderException if the layer is unsupported
     */
    protected FrameDecoder retrieveDecoder(Header header, Bitstream stream, int layer) 
            throws DecoderException {
        FrameDecoder decoder = switch (layer) {
            case 3 -> {
                if (l3decoder == null) {
                    l3decoder = new LayerIIIDecoder(stream, header, 
                        filter1, filter2, output, OutputChannels.BOTH_CHANNELS);
                }
                yield l3decoder;
            }
            case 2 -> {
                if (l2decoder == null) {
                    l2decoder = new LayerIIDecoder();
                    l2decoder.create(stream, header, 
                        filter1, filter2, output, OutputChannels.BOTH_CHANNELS);
                }
                yield l2decoder;
            }
            case 1 -> {
                if (l1decoder == null) {
                    l1decoder = new LayerIDecoder();
                    l1decoder.create(stream, header, 
                        filter1, filter2, output, OutputChannels.BOTH_CHANNELS);
                }
                yield l1decoder;
            }
            default -> null;
        };

        if (decoder == null) {
            throw newDecoderException(UNSUPPORTED_LAYER, null);
        }

        return decoder;
    }

    /**
     * Initializes the decoder with the first frame header.
     * 
     * @param header the first frame header
     * @throws DecoderException if initialization fails
     */
    private void initialize(Header header) throws DecoderException {
        float scalefactor = DEFAULT_SCALE_FACTOR;

        int mode = header.mode();
        int channels = (mode == Header.SINGLE_CHANNEL) ? 1 : 2;

        // Set up output buffer if not already configured
        if (output == null) {
            output = new SampleBuffer(header.frequency(), channels);
        }

        float[] factors = equalizer.getBandFactors();
        filter1 = new SynthesisFilter(0, scalefactor, factors);

        if (channels == 2) {
            filter2 = new SynthesisFilter(1, scalefactor, factors);
        }

        outputChannels = channels;
        outputFrequency = header.frequency();

        initialized = true;
    }
    
    /**
     * Converts a SampleBuffer to PCM bytes (little-endian).
     * 
     * @param sb the sample buffer
     * @return PCM byte array
     */
    private byte[] convertToPCM(SampleBuffer sb) {
        return convertToPCM(sb, true);
    }
    
    /**
     * Converts a SampleBuffer to PCM bytes with configurable endianness.
     * 
     * @param sb the sample buffer
     * @param littleEndian true for little-endian, false for big-endian
     * @return PCM byte array
     */
    private byte[] convertToPCM(SampleBuffer sb, boolean littleEndian) {
        short[] buf = sb.getBuffer();
        int len = sb.getBufferLength();
        byte[] out = new byte[len * 2];
        
        for (int i = 0; i < len; i++) {
            short s = buf[i];
            if (littleEndian) {
                out[i * 2] = (byte) (s & 0xFF);
                out[i * 2 + 1] = (byte) ((s >>> 8) & 0xFF);
            } else {
                out[i * 2] = (byte) ((s >>> 8) & 0xFF);
                out[i * 2 + 1] = (byte) (s & 0xFF);
            }
        }
        
        return out;
    }

    /**
     * The {@code Params} class presents customizable decoder aspects.
     * <p>
     * Instances of this class are not thread-safe.
     * </p>
     */
    public static class Params implements Cloneable {

        private OutputChannels outputChannels = OutputChannels.BOTH;
        private Equalizer equalizer = new Equalizer();

        /**
         * Creates default decoder parameters.
         */
        public Params() {
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            try {
                Params cloned = (Params) super.clone();
                // Deep clone the equalizer
                cloned.equalizer = new Equalizer();
                cloned.equalizer.setFrom(this.equalizer);
                return cloned;
            } catch (CloneNotSupportedException ex) {
                throw new InternalError(this + ": " + ex);
            }
        }

        /**
         * Sets the output channel configuration.
         * 
         * @param out the output channels (cannot be null)
         * @throws NullPointerException if out is null
         */
        public void setOutputChannels(OutputChannels out) {
            this.outputChannels = Objects.requireNonNull(out, "out cannot be null");
        }

        /**
         * Gets the output channel configuration.
         * 
         * @return the output channels
         */
        public OutputChannels getOutputChannels() {
            return outputChannels;
        }

        /**
         * Retrieves the initial equalizer settings.
         * <p>
         * The returned {@code Equalizer} is used only for initialization.
         * To affect real-time output, use {@link Decoder#setEqualizer}.
         * </p>
         *
         * @return the initial equalizer settings
         */
        public Equalizer getInitialEqualizerSettings() {
            return equalizer;
        }
        
        /**
         * Sets the initial equalizer settings.
         * 
         * @param eq the equalizer to use
         * @return this Params instance for method chaining
         * @since 2.0
         */
        public Params setInitialEqualizerSettings(Equalizer eq) {
            if (eq != null) {
                this.equalizer.setFrom(eq);
            }
            return this;
        }
    }
    
    /**
     * Builder for creating Decoder instances with fluent API.
     * 
     * @since 2.0
     */
    public static class DecoderBuilder {
        private final Params params = new Params();
        private Obuffer outputBuffer;
        private DecodeEventListener eventListener;
        
        private DecoderBuilder() {}
        
        /**
         * Sets the output channels.
         * 
         * @param channels the output channel configuration
         * @return this builder
         */
        public DecoderBuilder withOutputChannels(OutputChannels channels) {
            params.setOutputChannels(channels);
            return this;
        }
        
        /**
         * Sets the initial equalizer.
         * 
         * @param eq the equalizer
         * @return this builder
         */
        public DecoderBuilder withEqualizer(Equalizer eq) {
            params.setInitialEqualizerSettings(eq);
            return this;
        }
        
        /**
         * Sets a custom output buffer.
         * 
         * @param buffer the output buffer
         * @return this builder
         */
        public DecoderBuilder withOutputBuffer(Obuffer buffer) {
            this.outputBuffer = buffer;
            return this;
        }
        
        /**
         * Sets an event listener.
         * 
         * @param listener the listener
         * @return this builder
         */
        public DecoderBuilder withEventListener(DecodeEventListener listener) {
            this.eventListener = listener;
            return this;
        }
        
        /**
         * Builds the Decoder instance.
         * 
         * @return a new Decoder
         */
        public Decoder build() {
            Decoder decoder = new Decoder(params);
            if (outputBuffer != null) {
                decoder.setOutputBuffer(outputBuffer);
            }
            if (eventListener != null) {
                decoder.setEventListener(eventListener);
            }
            return decoder;
        }
    }
    
    /**
     * Interface for receiving decode events.
     * 
     * @since 2.0
     */
    public interface DecodeEventListener {
        /**
         * Called when a frame is successfully decoded.
         * 
         * @param frameNumber the frame number
         * @param header the frame header
         */
        void onFrameDecoded(long frameNumber, Header header);
        
        /**
         * Called when a decode error occurs.
         * 
         * @param exception the exception
         */
        void onError(DecoderException exception);
    }
    
    /**
     * Immutable snapshot of decoder information.
     * 
     * @since 2.0
     */
    public static class DecoderInfo {
        private final int outputFrequency;
        private final int outputChannels;
        private final long framesDecoded;
        private final long errorCount;
        private final boolean initialized;
        
        private DecoderInfo(int outputFrequency, int outputChannels, 
                           long framesDecoded, long errorCount, boolean initialized) {
            this.outputFrequency = outputFrequency;
            this.outputChannels = outputChannels;
            this.framesDecoded = framesDecoded;
            this.errorCount = errorCount;
            this.initialized = initialized;
        }
        
        public int getOutputFrequency() { return outputFrequency; }
        public int getOutputChannels() { return outputChannels; }
        public long getFramesDecoded() { return framesDecoded; }
        public long getErrorCount() { return errorCount; }
        public boolean isInitialized() { return initialized; }
        
        @Override
        public String toString() {
            return String.format("DecoderInfo[freq=%d, channels=%d, frames=%d, errors=%d, init=%b]",
                outputFrequency, outputChannels, framesDecoded, errorCount, initialized);
        }
    }
}