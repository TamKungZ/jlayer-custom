/*
 * 09/26/08     throw exception on subbband alloc error: Christopher G. Jennings (cjennings@acm.org)
 *
 * 11/19/04        1.0 moved to LGPL.
 *
 * 12/12/99        Initial version. Adapted from javalayer.java
 *                and Subband*.java. mdm@techie.com
 *
 * 02/28/99        Initial version : javalayer.java by E.B
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

/**
 * Implements decoding of MPEG Audio Layer I frames.
 * <p>
 * Layer I is the simplest of the three MPEG audio layers. It uses 384 samples
 * per frame and 32 subbands with relatively simple quantization.
 * </p>
 * 
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Supports mono and stereo modes</li>
 *   <li>Joint stereo with intensity stereo</li>
 *   <li>Optional CRC error detection</li>
 *   <li>12 samples per subband per frame</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b></p>
 * <p>This class is not thread-safe. Each thread should use its own decoder instance.</p>
 * 
 * @author MDM
 * @version 2.0
 * @since 0.0
 */
class LayerIDecoder implements FrameDecoder {

    /** Bitstream to read encoded data from */
    protected Bitstream stream;
    
    /** Header containing frame information */
    protected Header header;
    
    /** Synthesis filters for each channel */
    protected SynthesisFilter filter1, filter2;
    
    /** Output buffer for decoded samples */
    protected Obuffer buffer;
    
    /** Which channels to decode (BOTH, LEFT, RIGHT, DOWNMIX) */
    protected int whichChannels;
    
    /** Audio mode from header (mono, stereo, joint stereo, dual channel) */
    protected int mode;

    /** Number of subbands in this frame */
    protected int num_subbands;
    
    /** Array of subband decoders */
    protected Subband[] subbands;
    
    /** CRC checker, null if CRC not enabled */
    protected Crc16 crc;

    /**
     * Constructs a new Layer I decoder.
     * <p>
     * CRC checking is enabled by default.
     * </p>
     */
    public LayerIDecoder() {
        crc = new Crc16();
    }

    /**
     * Initializes the decoder with stream and filter parameters.
     * <p>
     * This method must be called before {@link #decodeFrame()}.
     * </p>
     * 
     * @param stream the bitstream to read from
     * @param header the frame header
     * @param filterA synthesis filter for left channel
     * @param filterB synthesis filter for right channel (may be null for mono)
     * @param buffer output buffer for decoded samples
     * @param whichCh which channels to decode (from {@link OutputChannels})
     */
    public void create(Bitstream stream, Header header,
                       SynthesisFilter filterA, SynthesisFilter filterB,
                       Obuffer buffer, int whichCh) {
        this.stream = stream;
        this.header = header;
        this.filter1 = filterA;
        this.filter2 = filterB;
        this.buffer = buffer;
        this.whichChannels = whichCh;
    }

    /**
     * Decodes one frame of Layer I audio.
     * <p>
     * This is the main decoding method. It reads allocation information,
     * scale factors, and sample data from the bitstream, then synthesizes
     * the output samples.
     * </p>
     * 
     * @throws DecoderException if decoding fails (corrupt data, CRC error, etc.)
     */
    @Override
    public void decodeFrame() throws DecoderException {
        num_subbands = header.numberOfSubbands();
        subbands = new Subband[32];
        mode = header.mode();

        createSubbands();
        readAllocation();
        readScaleFactorSelection();

        // Only proceed with sample decoding if CRC is OK (or not checked)
        if ((crc != null) || header.checksumOk()) {
            readScaleFactors();
            readSampleData();
        }
    }

    /**
     * Creates subband decoder instances based on the audio mode.
     * <p>
     * Different subband types are used for:
     * </p>
     * <ul>
     *   <li>Single channel (mono)</li>
     *   <li>Stereo (two independent channels)</li>
     *   <li>Joint stereo (with intensity stereo)</li>
     * </ul>
     */
    protected void createSubbands() {
        switch (mode) {
            case Header.SINGLE_CHANNEL -> {
                // Mono mode
                for (int i = 0; i < num_subbands; ++i) {
                    subbands[i] = new SubbandLayer1(i);
                }
            }
            case Header.JOINT_STEREO -> {
                // Joint stereo: normal stereo up to bound, then intensity stereo
                int bound = header.intensityStereoBound();
                for (int i = 0; i < bound; ++i) {
                    subbands[i] = new SubbandLayer1Stereo(i);
                }
                for (int i = bound; i < num_subbands; ++i) {
                    subbands[i] = new SubbandLayer1IntensityStereo(i);
                }
            }
            default -> {
                // Normal stereo or dual channel
                for (int i = 0; i < num_subbands; ++i) {
                    subbands[i] = new SubbandLayer1Stereo(i);
                }
            }
        }
    }

    /**
     * Reads allocation information for all subbands.
     * <p>
     * Allocation determines the number of bits used for each subband's samples.
     * </p>
     * 
     * @throws DecoderException if allocation is invalid (corrupt stream)
     */
    protected void readAllocation() throws DecoderException {
        for (int i = 0; i < num_subbands; ++i) {
            subbands[i].readAllocation(stream, header, null);
        }
    }

    /**
     * Reads scale factor selection information.
     * <p>
     * Layer I doesn't use scale factor selection, so this is a no-op.
     * This method exists for compatibility with Layer II decoder.
     * </p>
     */
    protected void readScaleFactorSelection() {
        // Not used in Layer I
    }

    /**
     * Reads scale factors for all subbands.
     * <p>
     * Scale factors are used to scale the decoded samples.
     * </p>
     */
    protected void readScaleFactors() {
        for (int i = 0; i < num_subbands; ++i) {
            subbands[i].readScaleFactor(stream, header);
        }
    }

    /**
     * Reads and processes all sample data for the frame.
     * <p>
     * This method implements the Layer I decoding loop:
     * </p>
     * <ol>
     *   <li>Read samples from bitstream</li>
     *   <li>Dequantize and scale samples</li>
     *   <li>Apply synthesis filter</li>
     *   <li>Output PCM samples</li>
     * </ol>
     */
    protected void readSampleData() {
        boolean readReady;
        boolean writeReady;
        
        do {
            // Read one sample from each subband
            readReady = true;
            for (int i = 0; i < num_subbands; ++i) {
                readReady = subbands[i].readSampleData(stream);
            }
            
            // Write samples to filters and generate PCM output
            do {
                writeReady = true;
                for (int i = 0; i < num_subbands; ++i) {
                    writeReady = subbands[i].put_next_sample(whichChannels, filter1, filter2);
                }

                // Calculate PCM samples from filtered subbands
                filter1.calculate_pcm_samples(buffer);
                
                // Process right channel for stereo
                if ((whichChannels == OutputChannels.BOTH_CHANNELS) && 
                    (mode != Header.SINGLE_CHANNEL)) {
                    filter2.calculate_pcm_samples(buffer);
                }
            } while (!writeReady);
        } while (!readReady);
    }

    /**
     * Abstract base class for Layer I and II subbands.
     * <p>
     * Each subband represents one of 32 frequency bands in the MPEG audio spectrum.
     * Subbands are processed independently during decoding.
     * </p>
     */
    static abstract class Subband {
        /**
         * Scale factors for Layer I and II (Annex 3-B.1 in ISO/IEC 11172).
         * <p>
         * These are pre-computed dequantization scale factors.
         * Index 63 is illegal but included to prevent array bounds errors.
         * </p>
         */
        public static final float[] scaleFactors = {
                2.00000000000000f, 1.58740105196820f, 1.25992104989487f, 1.00000000000000f,
                0.79370052598410f, 0.62996052494744f, 0.50000000000000f, 0.39685026299205f,
                0.31498026247372f, 0.25000000000000f, 0.19842513149602f, 0.15749013123686f,
                0.12500000000000f, 0.09921256574801f, 0.07874506561843f, 0.06250000000000f,
                0.04960628287401f, 0.03937253280921f, 0.03125000000000f, 0.02480314143700f,
                0.01968626640461f, 0.01562500000000f, 0.01240157071850f, 0.00984313320230f,
                0.00781250000000f, 0.00620078535925f, 0.00492156660115f, 0.00390625000000f,
                0.00310039267963f, 0.00246078330058f, 0.00195312500000f, 0.00155019633981f,
                0.00123039165029f, 0.00097656250000f, 0.00077509816991f, 0.00061519582514f,
                0.00048828125000f, 0.00038754908495f, 0.00030759791257f, 0.00024414062500f,
                0.00019377454248f, 0.00015379895629f, 0.00012207031250f, 0.00009688727124f,
                0.00007689947814f, 0.00006103515625f, 0.00004844363562f, 0.00003844973907f,
                0.00003051757813f, 0.00002422181781f, 0.00001922486954f, 0.00001525878906f,
                0.00001211090890f, 0.00000961243477f, 0.00000762939453f, 0.00000605545445f,
                0.00000480621738f, 0.00000381469727f, 0.00000302772723f, 0.00000240310869f,
                0.00000190734863f, 0.00000151386361f, 0.00000120155435f, 0.00000000000000f
        };

        /**
         * Reads allocation information from the bitstream.
         * 
         * @param stream bitstream to read from
         * @param header frame header
         * @param crc CRC checker (may be null)
         * @throws DecoderException if allocation is invalid
         */
        public abstract void readAllocation(Bitstream stream, Header header, Crc16 crc) 
            throws DecoderException;

        /**
         * Reads scale factor from the bitstream.
         * 
         * @param stream bitstream to read from
         * @param header frame header
         */
        public abstract void readScaleFactor(Bitstream stream, Header header);

        /**
         * Reads one sample from the bitstream.
         * 
         * @param stream bitstream to read from
         * @return true if all samples for this frame have been read
         */
        public abstract boolean readSampleData(Bitstream stream);

        /**
         * Dequantizes, scales, and outputs the next sample.
         * 
         * @param channels which channels to output
         * @param filter1 left/mono synthesis filter
         * @param filter2 right synthesis filter
         * @return true if all samples have been output
         */
        public abstract boolean put_next_sample(int channels, 
                                                 SynthesisFilter filter1, 
                                                 SynthesisFilter filter2);
    }

    /**
     * Layer I subband for single channel (mono) mode.
     * <p>
     * This is also the base class for intensity stereo subbands.
     * </p>
     */
    static class SubbandLayer1 extends Subband {

        /**
         * Requantization factors (Annex 3-B.2 in ISO/IEC 11172).
         * <p>
         * Used to convert quantized values back to floating-point samples.
         * Formula: factor = (1 / 2^n) * (2^(n+1) / (2^(n+1) - 1))
         * </p>
         */
        public static final float[] tableFactor = {
                0.0f, (1.0f / 2.0f) * (4.0f / 3.0f), (1.0f / 4.0f) * (8.0f / 7.0f), 
                (1.0f / 8.0f) * (16.0f / 15.0f), (1.0f / 16.0f) * (32.0f / 31.0f), 
                (1.0f / 32.0f) * (64.0f / 63.0f), (1.0f / 64.0f) * (128.0f / 127.0f),
                (1.0f / 128.0f) * (256.0f / 255.0f), (1.0f / 256.0f) * (512.0f / 511.0f),
                (1.0f / 512.0f) * (1024.0f / 1023.0f), (1.0f / 1024.0f) * (2048.0f / 2047.0f),
                (1.0f / 2048.0f) * (4096.0f / 4095.0f), (1.0f / 4096.0f) * (8192.0f / 8191.0f),
                (1.0f / 8192.0f) * (16384.0f / 16383.0f), (1.0f / 16384.0f) * (32768.0f / 32767.0f)
        };

        /**
         * Requantization offsets.
         * <p>
         * Used in conjunction with factors to dequantize samples.
         * Formula: offset = ((1 / 2^n) - 1) * (2^(n+1) / (2^(n+1) - 1))
         * </p>
         */
        public static final float[] tableOffset = {
                0.0f, ((1.0f / 2.0f) - 1.0f) * (4.0f / 3.0f), ((1.0f / 4.0f) - 1.0f) * (8.0f / 7.0f), 
                ((1.0f / 8.0f) - 1.0f) * (16.0f / 15.0f), ((1.0f / 16.0f) - 1.0f) * (32.0f / 31.0f), 
                ((1.0f / 32.0f) - 1.0f) * (64.0f / 63.0f), ((1.0f / 64.0f) - 1.0f) * (128.0f / 127.0f),
                ((1.0f / 128.0f) - 1.0f) * (256.0f / 255.0f), ((1.0f / 256.0f) - 1.0f) * (512.0f / 511.0f),
                ((1.0f / 512.0f) - 1.0f) * (1024.0f / 1023.0f), ((1.0f / 1024.0f) - 1.0f) * (2048.0f / 2047.0f),
                ((1.0f / 2048.0f) - 1.0f) * (4096.0f / 4095.0f), ((1.0f / 4096.0f) - 1.0f) * (8192.0f / 8191.0f),
                ((1.0f / 8192.0f) - 1.0f) * (16384.0f / 16383.0f), ((1.0f / 16384.0f) - 1.0f) * (32768.0f / 32767.0f)
        };

        /** Subband number (0-31) */
        protected int subbandNumber;
        
        /** Current sample number within frame (0-11) */
        protected int sampleNumber;
        
        /** Number of bits allocated for this subband */
        protected int allocation;
        
        /** Scale factor for this subband */
        protected float scaleFactor;
        
        /** Number of bits per sample */
        protected int sampleLength;
        
        /** Current quantized sample value */
        protected float sample;
        
        /** Dequantization factor and offset */
        protected float factor, offset;

        /**
         * Constructs a new Layer I subband.
         * 
         * @param subbandNumber the subband index (0-31)
         */
        public SubbandLayer1(int subbandNumber) {
            this.subbandNumber = subbandNumber;
            this.sampleNumber = 0;
        }

        @Override
        public void readAllocation(Bitstream stream, Header header, Crc16 crc) 
                throws DecoderException {
            allocation = stream.getBits(4);
            
            // Allocation value 15 is reserved and indicates corrupt stream
            if (allocation == 15) {
                throw new DecoderException(DecoderErrors.ILLEGAL_SUBBAND_ALLOCATION, null)
                    .withContext("subband", subbandNumber)
                    .withContext("allocation", allocation);
            }
            
            if (allocation != 0) {
                sampleLength = allocation + 1;
                factor = tableFactor[allocation];
                offset = tableOffset[allocation];
            }
        }

        @Override
        public void readScaleFactor(Bitstream stream, Header header) {
            if (allocation != 0) {
                scaleFactor = scaleFactors[stream.getBits(6)];
            }
        }

        @Override
        public boolean readSampleData(Bitstream stream) {
            if (allocation != 0) {
                sample = stream.getBits(sampleLength);
            }
            
            // Layer I has 12 samples per subband
            if (++sampleNumber == 12) {
                sampleNumber = 0;
                return true;  // All samples read
            }
            return false;  // More samples to read
        }

        @Override
        public boolean put_next_sample(int channels, SynthesisFilter filter1, SynthesisFilter filter2) {
            // Only output if allocated and not right-channel-only
            if ((allocation != 0) && (channels != OutputChannels.RIGHT_CHANNEL)) {
                // Dequantize: sample * factor + offset, then scale
                float scaled_sample = (sample * factor + offset) * scaleFactor;
                filter1.inputSample(scaled_sample, subbandNumber);
            }
            return true;
        }
    }

    /**
     * Layer I subband for joint stereo intensity stereo mode.
     * <p>
     * In intensity stereo mode, one set of samples is shared between channels
     * but scaled differently for each channel to create stereo image.
     * </p>
     */
    static class SubbandLayer1IntensityStereo extends SubbandLayer1 {
        /** Scale factor for right channel */
        protected float channel2ScaleFactor;

        /**
         * Constructs a new intensity stereo subband.
         * 
         * @param subbandNumber the subband index (0-31)
         */
        public SubbandLayer1IntensityStereo(int subbandNumber) {
            super(subbandNumber);
        }

        @Override
        public void readAllocation(Bitstream stream, Header header, Crc16 crc) 
                throws DecoderException {
            super.readAllocation(stream, header, crc);
        }

        @Override
        public void readScaleFactor(Bitstream stream, Header header) {
            if (allocation != 0) {
                // Read scale factors for both channels
                scaleFactor = scaleFactors[stream.getBits(6)];
                channel2ScaleFactor = scaleFactors[stream.getBits(6)];
            }
        }

        @Override
        public boolean readSampleData(Bitstream stream) {
            return super.readSampleData(stream);
        }

        @Override
        public boolean put_next_sample(int channels, SynthesisFilter filter1, SynthesisFilter filter2) {
            if (allocation != 0) {
                // Dequantize once
                float dequantized = sample * factor + offset;
                
                switch (channels) {
                    case OutputChannels.BOTH_CHANNELS -> {
                        // Scale separately for each channel
                        float sample1 = dequantized * scaleFactor;
                        float sample2 = dequantized * channel2ScaleFactor;
                        filter1.inputSample(sample1, subbandNumber);
                        filter2.inputSample(sample2, subbandNumber);
                    }
                    case OutputChannels.LEFT_CHANNEL -> {
                        float sample1 = dequantized * scaleFactor;
                        filter1.inputSample(sample1, subbandNumber);
                    }
                    case OutputChannels.RIGHT_CHANNEL -> {
                        float sample2 = dequantized * channel2ScaleFactor;
                        filter1.inputSample(sample2, subbandNumber);
                    }
                }
            }
            return true;
        }
    }

    /**
     * Layer I subband for normal stereo mode.
     * <p>
     * Each channel has independent allocation, scale factors, and samples.
     * </p>
     */
    static class SubbandLayer1Stereo extends SubbandLayer1 {

        /** Right channel allocation */
        protected int channel2Allocation;
        
        /** Right channel scale factor */
        protected float channel2ScaleFactor;
        
        /** Right channel sample length in bits */
        protected int channel2SampleLength;
        
        /** Right channel quantized sample */
        protected float channel2Sample;
        
        /** Right channel dequantization factor and offset */
        protected float channel2Factor, channel2Offset;

        /**
         * Constructs a new stereo subband.
         * 
         * @param subbandNumber the subband index (0-31)
         */
        public SubbandLayer1Stereo(int subbandNumber) {
            super(subbandNumber);
        }

        @Override
        public void readAllocation(Bitstream stream, Header header, Crc16 crc) 
                throws DecoderException {
            // Read allocations for both channels
            allocation = stream.getBits(4);
            channel2Allocation = stream.getBits(4);
            
            // Setup left channel
            if (allocation != 0) {
                sampleLength = allocation + 1;
                factor = tableFactor[allocation];
                offset = tableOffset[allocation];
            }
            
            // Setup right channel
            if (channel2Allocation != 0) {
                channel2SampleLength = channel2Allocation + 1;
                channel2Factor = tableFactor[channel2Allocation];
                channel2Offset = tableOffset[channel2Allocation];
            }
        }

        @Override
        public void readScaleFactor(Bitstream stream, Header header) {
            if (allocation != 0) {
                scaleFactor = scaleFactors[stream.getBits(6)];
            }
            if (channel2Allocation != 0) {
                channel2ScaleFactor = scaleFactors[stream.getBits(6)];
            }
        }

        @Override
        public boolean readSampleData(Bitstream stream) {
            boolean returnValue = super.readSampleData(stream);
            
            // Read right channel sample
            if (channel2Allocation != 0) {
                channel2Sample = stream.getBits(channel2SampleLength);
            }
            
            return returnValue;
        }

        @Override
        public boolean put_next_sample(int channels, SynthesisFilter filter1, SynthesisFilter filter2) {
            // Output left channel
            super.put_next_sample(channels, filter1, filter2);
            
            // Output right channel if needed
            if ((channel2Allocation != 0) && (channels != OutputChannels.LEFT_CHANNEL)) {
                float sample2 = (channel2Sample * channel2Factor + channel2Offset) * 
                                channel2ScaleFactor;
                
                if (channels == OutputChannels.BOTH_CHANNELS) {
                    filter2.inputSample(sample2, subbandNumber);
                } else {
                    // RIGHT_CHANNEL only
                    filter1.inputSample(sample2, subbandNumber);
                }
            }
            return true;
        }
    }
}