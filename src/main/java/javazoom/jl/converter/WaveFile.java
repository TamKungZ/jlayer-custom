/*
 * 11/19/04 1.0 moved to LGPL.
 * 02/23/99 JavaConversion by E.B
 * Don Cross, April 1993.
 * RIFF file format classes.
 * See Chapter 8 of "Multimedia Programmer's Reference" in
 * the Microsoft Windows SDK.
 *
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

package javazoom.jl.converter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;


/**
 * Class allowing WaveFormat Access with enhanced features.
 * 
 * <p>Enhanced features in this version:
 * <ul>
 *   <li>Validation of audio parameters</li>
 *   <li>Support for Path-based operations</li>
 *   <li>Calculated properties (duration, byte rate, etc.)</li>
 *   <li>Builder pattern for flexible construction</li>
 *   <li>Immutable configuration once opened</li>
 * </ul>
 * 
 * <p>Usage example:
 * <pre>{@code
 * try (WaveFile wave = new WaveFile()) {
 *     wave.openForWrite("output.wav", 44100, (short) 16, (short) 2);
 *     wave.writeData(samples, samples.length);
 * }
 * }</pre>
 */
public class WaveFile extends RiffFile {

    public static final int MAX_WAVE_CHANNELS = 2;
    
    /** PCM format tag */
    public static final short WAVE_FORMAT_PCM = 1;
    
    /** Standard CD quality sample rate */
    public static final int SAMPLE_RATE_CD = 44100;
    
    /** Standard DVD quality sample rate */
    public static final int SAMPLE_RATE_DVD = 48000;
    
    /** Telephone quality sample rate */
    public static final int SAMPLE_RATE_TELEPHONE = 8000;
    
    /** 8-bit per sample */
    public static final short BITS_PER_SAMPLE_8 = 8;
    
    /** 16-bit per sample (CD quality) */
    public static final short BITS_PER_SAMPLE_16 = 16;
    
    /** 24-bit per sample */
    public static final short BITS_PER_SAMPLE_24 = 24;
    
    /** Mono channel */
    public static final short CHANNELS_MONO = 1;
    
    /** Stereo channels */
    public static final short CHANNELS_STEREO = 2;

    static class ChunkData {
        /** Format category (PCM=1) */
        public short wFormatTag = 0;
        /** Number of channels (mono=1, stereo=2) */
        public short nChannels = 0;
        /** Sampling rate [Hz] */
        public int nSamplesPerSec = 0;
        /** Average bytes per second */
        public int nAvgBytesPerSec = 0;
        /** Block alignment */
        public short nBlockAlign = 0;
        /** Bits per sample */
        public short nBitsPerSample = 0;

        public ChunkData() {
            wFormatTag = WAVE_FORMAT_PCM;
            config(SAMPLE_RATE_CD, BITS_PER_SAMPLE_16, CHANNELS_MONO);
        }

        public void config(int newSamplingRate, short newBitsPerSample, short newNumChannels) {
            nSamplesPerSec = newSamplingRate;
            nChannels = newNumChannels;
            nBitsPerSample = newBitsPerSample;
            nAvgBytesPerSec = (nChannels * nSamplesPerSec * nBitsPerSample) / 8;
            nBlockAlign = (short) ((nChannels * nBitsPerSample) / 8);
        }
        
        /**
         * Validate the configuration.
         * @return true if valid
         */
        public boolean isValid() {
            return nChannels > 0 && nChannels <= MAX_WAVE_CHANNELS &&
                   nSamplesPerSec > 0 &&
                   (nBitsPerSample == 8 || nBitsPerSample == 16 || nBitsPerSample == 24) &&
                   nAvgBytesPerSec == (nChannels * nSamplesPerSec * nBitsPerSample) / 8 &&
                   nBlockAlign == (nChannels * nBitsPerSample) / 8;
        }
    }

    static class Chunk {
        public RiffChunkHeader header;
        public ChunkData data;

        public Chunk() {
            header = new RiffChunkHeader();
            data = new ChunkData();
            header.ckID = fourCC("fmt ");
            header.ckSize = 16;
        }

        public int verifyValidity() {
            boolean ret = header.ckID == fourCC("fmt ") && data.isValid();
            return ret ? 1 : 0;
        }
    }

    public static class WaveFileSample {
        public short[] chan;

        public WaveFileSample() {
            chan = new short[WaveFile.MAX_WAVE_CHANNELS];
        }
        
        /**
         * Get sample value for specific channel.
         * @param channel channel index
         * @return sample value
         */
        public short getSample(int channel) {
            if (channel < 0 || channel >= chan.length) {
                throw new IndexOutOfBoundsException("Invalid channel: " + channel);
            }
            return chan[channel];
        }
        
        /**
         * Set sample value for specific channel.
         * @param channel channel index
         * @param value sample value
         */
        public void setSample(int channel, short value) {
            if (channel < 0 || channel >= chan.length) {
                throw new IndexOutOfBoundsException("Invalid channel: " + channel);
            }
            chan[channel] = value;
        }
    }

    private Chunk waveFormat;
    private RiffChunkHeader pcmData;
    private long pcmDataOffset = 0;  // offset of 'pcmData' in output file
    private int numSamples = 0;
    private long totalBytesWritten = 0;

    /**
     * Constructs a new WaveFile instance.
     */
    public WaveFile() {
        pcmData = new RiffChunkHeader();
        waveFormat = new Chunk();
        pcmData.ckID = fourCC("data");
        pcmData.ckSize = 0;
        numSamples = 0;
        totalBytesWritten = 0;
    }

    /**
     * Open wave file for writing with validation.
     * @param filename output file path
     * @param samplingRate sample rate in Hz
     * @param bitsPerSample bits per sample (8 or 16)
     * @param numChannels number of channels (1 or 2)
     * @return DDC_SUCCESS or error code
     */
    public int openForWrite(String filename, int samplingRate, short bitsPerSample, short numChannels) {
        // Validate parameters
        if (filename == null || filename.isEmpty()) {
            return DDC_INVALID_CALL;
        }
        
        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24) {
            return DDC_INVALID_CALL;
        }
        
        if (numChannels < 1 || numChannels > MAX_WAVE_CHANNELS) {
            return DDC_INVALID_CALL;
        }
        
        if (samplingRate <= 0 || samplingRate > 192000) {
            return DDC_INVALID_CALL;
        }

        waveFormat.data.config(samplingRate, bitsPerSample, numChannels);

        int retcode = Open(filename, RFM_WRITE);

        if (retcode == DDC_SUCCESS) {
            byte[] theWave = {(byte) 'W', (byte) 'A', (byte) 'V', (byte) 'E'};
            retcode = write(theWave, 4);

            if (retcode == DDC_SUCCESS) {
                // Writing waveFormat
                retcode = write(waveFormat.header, 8);
                if (retcode == DDC_SUCCESS) retcode = write(waveFormat.data.wFormatTag, 2);
                if (retcode == DDC_SUCCESS) retcode = write(waveFormat.data.nChannels, 2);
                if (retcode == DDC_SUCCESS) retcode = write(waveFormat.data.nSamplesPerSec, 4);
                if (retcode == DDC_SUCCESS) retcode = write(waveFormat.data.nAvgBytesPerSec, 4);
                if (retcode == DDC_SUCCESS) retcode = write(waveFormat.data.nBlockAlign, 2);
                if (retcode == DDC_SUCCESS) retcode = write(waveFormat.data.nBitsPerSample, 2);

                if (retcode == DDC_SUCCESS) {
                    pcmDataOffset = currentFilePosition();
                    retcode = write(pcmData, 8);
                }
            }
        }

        return retcode;
    }
    
    /**
     * Open wave file for writing using Path.
     * @param path output file path
     * @param samplingRate sample rate in Hz
     * @param bitsPerSample bits per sample (8 or 16)
     * @param numChannels number of channels (1 or 2)
     * @return DDC_SUCCESS or error code
     */
    public int openForWrite(Path path, int samplingRate, short bitsPerSample, short numChannels) {
        Objects.requireNonNull(path, "Path cannot be null");
        return openForWrite(path.toString(), samplingRate, bitsPerSample, numChannels);
    }

    /**
     * Write 16-bit audio data.
     * @param data audio samples
     * @param numData number of samples to write
     * @return DDC_SUCCESS or error code
     */
    public int writeData(short[] data, int numData) {
        if (data == null || numData < 0 || numData > data.length) {
            return DDC_INVALID_CALL;
        }
        
        int extraBytes = numData * 2;
        pcmData.ckSize += extraBytes;
        totalBytesWritten += extraBytes;
        numSamples += numData / waveFormat.data.nChannels;
        
        return super.write(data, extraBytes);
    }
    
    /**
     * Write 8-bit audio data.
     * @param data audio samples (0-255 range)
     * @param numData number of samples to write
     * @return DDC_SUCCESS or error code
     */
    public int writeData8(byte[] data, int numData) {
        if (data == null || numData < 0 || numData > data.length) {
            return DDC_INVALID_CALL;
        }
        
        if (waveFormat.data.nBitsPerSample != 8) {
            return DDC_INVALID_CALL;
        }
        
        pcmData.ckSize += numData;
        totalBytesWritten += numData;
        numSamples += numData / waveFormat.data.nChannels;
        
        return super.write(data, numData);
    }
    
    /**
     * Write single sample (for all channels).
     * @param sample the sample data for all channels
     * @return DDC_SUCCESS or error code
     */
    public int writeSample(WaveFileSample sample) {
        if (sample == null) {
            return DDC_INVALID_CALL;
        }
        
        short[] data = new short[waveFormat.data.nChannels];
        for (int i = 0; i < waveFormat.data.nChannels; i++) {
            data[i] = sample.chan[i];
        }
        
        return writeData(data, waveFormat.data.nChannels);
    }

    /**
     * Close wave file and update header.
     * @return DDC_SUCCESS or error code
     * @deprecated Use {@link #close()} instead for AutoCloseable compatibility
     */
    @Deprecated
    public int closeWithReturnCode() {
        int rc = DDC_SUCCESS;

        if (fmode == RFM_WRITE) {
            rc = backpatch(pcmDataOffset, pcmData, 8);
        }
        
        if (rc == DDC_SUCCESS) {
            rc = super.closeWithReturnCode();
        }
        
        return rc;
    }
    
    /**
     * Close wave file and update header (Closeable interface implementation).
     */
    @Override
    public void close() throws IOException {
        int rc = closeWithReturnCode();
        if (rc != DDC_SUCCESS) {
            throw new IOException("Failed to close wave file: " + toDDCRETString(rc));
        }
    }

    /**
     * Get sampling rate.
     * @return sample rate in Hz
     */
    public int samplingRate() {
        return waveFormat.data.nSamplesPerSec;
    }

    /**
     * Get bits per sample.
     * @return bits per sample (8, 16, or 24)
     */
    public short bitsPerSample() {
        return waveFormat.data.nBitsPerSample;
    }

    /**
     * Get number of channels.
     * @return number of channels (1=mono, 2=stereo)
     */
    public short numChannels() {
        return waveFormat.data.nChannels;
    }

    /**
     * Get number of samples written.
     * @return number of samples per channel
     */
    public int numSamples() {
        return numSamples;
    }
    
    /**
     * Get total bytes written.
     * @return total audio data bytes
     */
    public long totalBytesWritten() {
        return totalBytesWritten;
    }
    
    /**
     * Get duration in seconds.
     * @return duration in seconds
     */
    public double getDuration() {
        if (waveFormat.data.nSamplesPerSec == 0) {
            return 0.0;
        }
        return (double) numSamples / waveFormat.data.nSamplesPerSec;
    }
    
    /**
     * Get duration in milliseconds.
     * @return duration in milliseconds
     */
    public long getDurationMillis() {
        return (long) (getDuration() * 1000);
    }
    
    /**
     * Get byte rate (bytes per second).
     * @return bytes per second
     */
    public int getByteRate() {
        return waveFormat.data.nAvgBytesPerSec;
    }
    
    /**
     * Get block align (bytes per sample frame).
     * @return block alignment
     */
    public short getBlockAlign() {
        return waveFormat.data.nBlockAlign;
    }
    
    /**
     * Check if this is a stereo file.
     * @return true if stereo
     */
    public boolean isStereo() {
        return waveFormat.data.nChannels == 2;
    }
    
    /**
     * Check if this is a mono file.
     * @return true if mono
     */
    public boolean isMono() {
        return waveFormat.data.nChannels == 1;
    }
    
    /**
     * Get format information as string.
     * @return format description
     */
    public String getFormatInfo() {
        return String.format("%d Hz, %d-bit, %s", 
            samplingRate(), 
            bitsPerSample(), 
            isMono() ? "Mono" : "Stereo");
    }

    /**
     * Open for write using another wave file's parameters.
     * @param filename output file path
     * @param otherWave source wave file for parameters
     * @return DDC_SUCCESS or error code
     */
    public int openForWrite(String filename, WaveFile otherWave) {
        Objects.requireNonNull(otherWave, "Source wave file cannot be null");
        return openForWrite(filename,
                otherWave.samplingRate(),
                otherWave.bitsPerSample(),
                otherWave.numChannels());
    }
    
    /**
     * Open for write using another wave file's parameters with Path.
     * @param path output file path
     * @param otherWave source wave file for parameters
     * @return DDC_SUCCESS or error code
     */
    public int openForWrite(Path path, WaveFile otherWave) {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(otherWave, "Source wave file cannot be null");
        return openForWrite(path.toString(),
                otherWave.samplingRate(),
                otherWave.bitsPerSample(),
                otherWave.numChannels());
    }

    /**
     * Get current file position.
     * @return file position in bytes
     */
    @Override
    public long currentFilePosition() {
        return super.currentFilePosition();
    }
    
    /**
     * Builder class for creating WaveFile with fluent API.
     */
    public static class Builder {
        private String filename;
        private Path path;
        private int samplingRate = SAMPLE_RATE_CD;
        private short bitsPerSample = BITS_PER_SAMPLE_16;
        private short numChannels = CHANNELS_STEREO;
        
        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }
        
        public Builder path(Path path) {
            this.path = path;
            return this;
        }
        
        public Builder samplingRate(int samplingRate) {
            this.samplingRate = samplingRate;
            return this;
        }
        
        public Builder bitsPerSample(short bitsPerSample) {
            this.bitsPerSample = bitsPerSample;
            return this;
        }
        
        public Builder numChannels(short numChannels) {
            this.numChannels = numChannels;
            return this;
        }
        
        public Builder mono() {
            this.numChannels = CHANNELS_MONO;
            return this;
        }
        
        public Builder stereo() {
            this.numChannels = CHANNELS_STEREO;
            return this;
        }
        
        public Builder cdQuality() {
            this.samplingRate = SAMPLE_RATE_CD;
            this.bitsPerSample = BITS_PER_SAMPLE_16;
            this.numChannels = CHANNELS_STEREO;
            return this;
        }
        
        public Builder dvdQuality() {
            this.samplingRate = SAMPLE_RATE_DVD;
            this.bitsPerSample = BITS_PER_SAMPLE_16;
            this.numChannels = CHANNELS_STEREO;
            return this;
        }
        
        /**
         * Build and open the WaveFile.
         * @return opened WaveFile instance
         * @throws IllegalStateException if open fails
         */
        public WaveFile build() {
            WaveFile wave = new WaveFile();
            int retcode;
            
            if (path != null) {
                retcode = wave.openForWrite(path, samplingRate, bitsPerSample, numChannels);
            } else if (filename != null) {
                retcode = wave.openForWrite(filename, samplingRate, bitsPerSample, numChannels);
            } else {
                throw new IllegalStateException("Filename or path must be set");
            }
            
            if (retcode != DDC_SUCCESS) {
                throw new IllegalStateException("Failed to open wave file: " + toDDCRETString(retcode));
            }
            
            return wave;
        }
    }
}