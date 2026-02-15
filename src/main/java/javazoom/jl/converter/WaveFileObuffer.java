/*
 * 11/19/04  1.0 moved to LGPL.
 *
 * 12/12/99     0.0.7 Renamed class, additional constructor arguments
 *             and larger write buffers. mdm@techie.com.
 *
 * 15/02/99  Java Conversion by E.B ,javalayer@javazoom.net
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
import java.util.Arrays;
import java.util.Objects;

import javazoom.jl.decoder.Obuffer;


/**
 * Implements an {@link Obuffer} by writing the data to
 * a file in RIFF WAVE format with enhanced performance and features.
 * 
 * <p>Enhanced features in this version:
 * <ul>
 *   <li>Automatic buffer management with configurable size</li>
 *   <li>Better error handling with detailed error reporting</li>
 *   <li>Statistics tracking (samples written, errors, etc.)</li>
 *   <li>Support for Path-based operations</li>
 *   <li>Thread-safe operations</li>
 *   <li>Optional listener for progress monitoring</li>
 * </ul>
 *
 * @since 0.0
 */
public class WaveFileObuffer extends Obuffer {

    /** Default buffer size multiplier */
    private static final int DEFAULT_BUFFER_MULTIPLIER = 2;
    
    private final short[] buffer;
    private final short[] bufferP;
    private final int channels;
    private final WaveFile outWave;
    private final int bufferSize;
    
    /** Statistics */
    private long totalSamplesWritten = 0;
    private long totalBytesWritten = 0;
    private int writeCount = 0;
    private int errorCount = 0;
    private boolean isClosed = false;
    
    /** Optional progress listener */
    private ProgressListener progressListener;
    
    /** Lock for thread safety */
    private final Object lock = new Object();

    /**
     * Creates a new WaveFileObuffer instance.
     *
     * @param number_of_channels The number of channels of audio data
     *                           this buffer will receive.
     * @param freq               The sample frequency of the samples in the buffer.
     * @param fileName           The filename to write the data to.
     */
    public WaveFileObuffer(int number_of_channels, int freq, String fileName) {
        this(number_of_channels, freq, fileName, OBUFFERSIZE * DEFAULT_BUFFER_MULTIPLIER);
    }
    
    /**
     * Creates a new WaveFileObuffer instance with custom buffer size.
     *
     * @param number_of_channels The number of channels of audio data
     * @param freq               The sample frequency of the samples
     * @param fileName           The filename to write the data to
     * @param bufferSize         Custom buffer size
     */
    public WaveFileObuffer(int number_of_channels, int freq, String fileName, int bufferSize) {
        Objects.requireNonNull(fileName, "fileName cannot be null");
        
        if (number_of_channels < 1 || number_of_channels > MAXCHANNELS) {
            throw new IllegalArgumentException("Invalid number of channels: " + number_of_channels);
        }
        
        if (freq <= 0) {
            throw new IllegalArgumentException("Invalid frequency: " + freq);
        }
        
        if (bufferSize < OBUFFERSIZE) {
            throw new IllegalArgumentException("Buffer size too small: " + bufferSize);
        }

        this.bufferSize = bufferSize;
        this.buffer = new short[bufferSize];
        this.bufferP = new short[MAXCHANNELS];
        this.channels = number_of_channels;

        for (int i = 0; i < number_of_channels; ++i) {
            bufferP[i] = (short) i;
        }

        this.outWave = new WaveFile();

        int rc = outWave.openForWrite(fileName, freq, (short) 16, (short) channels);
        
        if (rc != WaveFile.DDC_SUCCESS) {
            throw new RuntimeException("Failed to open wave file: " + WaveFile.toDDCRETString(rc));
        }
    }
    
    /**
     * Creates a new WaveFileObuffer instance using Path.
     *
     * @param number_of_channels The number of channels
     * @param freq               The sample frequency
     * @param path               The file path to write to
     */
    public WaveFileObuffer(int number_of_channels, int freq, Path path) {
        this(number_of_channels, freq, path.toString());
    }
    
    /**
     * Creates a new WaveFileObuffer instance with Path and custom buffer size.
     *
     * @param number_of_channels The number of channels
     * @param freq               The sample frequency
     * @param path               The file path to write to
     * @param bufferSize         Custom buffer size
     */
    public WaveFileObuffer(int number_of_channels, int freq, Path path, int bufferSize) {
        this(number_of_channels, freq, path.toString(), bufferSize);
    }

    /**
     * Takes a 16 Bit PCM sample.
     */
    @Override
    public void append(int channel, short value) {
        synchronized (lock) {
            if (isClosed) {
                throw new IllegalStateException("Buffer is closed");
            }
            
            if (channel < 0 || channel >= channels) {
                throw new IllegalArgumentException("Invalid channel: " + channel);
            }
            
            buffer[bufferP[channel]] = value;
            bufferP[channel] += (short) channels;
        }
    }

    /**
     * Write the samples to the file.
     */
    @Override
    public void writeBuffer(int val) {
        synchronized (lock) {
            if (isClosed) {
                return;
            }
            
            int samplesToWrite = bufferP[0];
            
            if (samplesToWrite > 0) {
                int rc = outWave.writeData(buffer, samplesToWrite);
                
                if (rc == WaveFile.DDC_SUCCESS) {
                    totalSamplesWritten += samplesToWrite;
                    totalBytesWritten += samplesToWrite * 2; // 16-bit samples
                    writeCount++;
                    
                    if (progressListener != null) {
                        progressListener.onProgress(totalSamplesWritten, totalBytesWritten, writeCount);
                    }
                } else {
                    errorCount++;
                    if (progressListener != null) {
                        progressListener.onError("Write error: " + WaveFile.toDDCRETString(rc));
                    }
                }
                
                // Reset buffer positions
                for (int i = 0; i < channels; ++i) {
                    bufferP[i] = (short) i;
                }
            }
        }
    }
    
    /**
     * Flush any remaining data in the buffer.
     */
    public void flush() {
        synchronized (lock) {
            if (!isClosed && bufferP[0] > 0) {
                writeBuffer(0);
            }
        }
    }

    /**
     * Close the output file and flush remaining data.
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (isClosed) {
                return;
            }
            
            // Flush any remaining data
            flush();
            
            // Close the wave file (handle IOException)
            try {
                outWave.close();
            } catch (IOException e) {
                errorCount++;
                if (progressListener != null) {
                    progressListener.onError("Error closing wave file: " + e.getMessage());
                }
            }
            isClosed = true;
            
            if (progressListener != null) {
                progressListener.onComplete(totalSamplesWritten, totalBytesWritten, writeCount);
            }
        }
    }

    /**
     * Clear buffer without writing.
     */
    @Override
    public void clearBuffer() {
        synchronized (lock) {
            Arrays.fill(buffer, (short) 0);
            for (int i = 0; i < channels; ++i) {
                bufferP[i] = (short) i;
            }
        }
    }

    /**
     * Set stop flag (deprecated, use close() instead).
     */
    @Override
    public void setStopFlag() {
        // Legacy compatibility - does nothing
    }
    
    /**
     * Get total samples written.
     * @return total number of samples
     */
    public long getTotalSamplesWritten() {
        return totalSamplesWritten;
    }
    
    /**
     * Get total bytes written.
     * @return total bytes written
     */
    public long getTotalBytesWritten() {
        return totalBytesWritten;
    }
    
    /**
     * Get write operation count.
     * @return number of write operations
     */
    public int getWriteCount() {
        return writeCount;
    }
    
    /**
     * Get error count.
     * @return number of errors encountered
     */
    public int getErrorCount() {
        return errorCount;
    }
    
    /**
     * Check if buffer is closed.
     * @return true if closed
     */
    public boolean isClosed() {
        return isClosed;
    }
    
    /**
     * Get the number of channels.
     * @return channel count
     */
    public int getChannels() {
        return channels;
    }
    
    /**
     * Get buffer size.
     * @return buffer size in samples
     */
    public int getBufferSize() {
        return bufferSize;
    }
    
    /**
     * Get current buffer fill level.
     * @return number of samples in buffer
     */
    public int getBufferFill() {
        synchronized (lock) {
            return bufferP[0];
        }
    }
    
    /**
     * Check if buffer is full.
     * @return true if buffer is full
     */
    public boolean isBufferFull() {
        synchronized (lock) {
            return bufferP[0] >= buffer.length;
        }
    }
    
    /**
     * Get the WaveFile instance (for advanced usage).
     * @return the underlying WaveFile
     */
    public WaveFile getWaveFile() {
        return outWave;
    }
    
    /**
     * Set progress listener.
     * @param listener the progress listener
     */
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }
    
    /**
     * Get statistics as formatted string.
     * @return statistics information
     */
    public String getStatistics() {
        return String.format(
            "Statistics: Samples=%d, Bytes=%d, Writes=%d, Errors=%d, Duration=%.2fs",
            totalSamplesWritten,
            totalBytesWritten,
            writeCount,
            errorCount,
            outWave.getDuration()
        );
    }
    
    /**
     * Interface for monitoring buffer operations.
     */
    public interface ProgressListener {
        /**
         * Called on each successful write.
         * @param totalSamples total samples written so far
         * @param totalBytes total bytes written so far
         * @param writeCount number of write operations
         */
        void onProgress(long totalSamples, long totalBytes, int writeCount);
        
        /**
         * Called when an error occurs.
         * @param errorMessage error description
         */
        void onError(String errorMessage);
        
        /**
         * Called when buffer is closed.
         * @param totalSamples final sample count
         * @param totalBytes final byte count
         * @param writeCount final write count
         */
        void onComplete(long totalSamples, long totalBytes, int writeCount);
    }
    
    /**
     * Simple adapter for ProgressListener.
     */
    public static class ProgressAdapter implements ProgressListener {
        @Override
        public void onProgress(long totalSamples, long totalBytes, int writeCount) {
            // Default implementation does nothing
        }
        
        @Override
        public void onError(String errorMessage) {
            // Default implementation does nothing
        }
        
        @Override
        public void onComplete(long totalSamples, long totalBytes, int writeCount) {
            // Default implementation does nothing
        }
    }
    
    /**
     * Builder class for creating WaveFileObuffer with fluent API.
     */
    public static class Builder {
        private int channels = 2;
        private int frequency = 44100;
        private String filename;
        private Path path;
        private int bufferSize = OBUFFERSIZE * DEFAULT_BUFFER_MULTIPLIER;
        private ProgressListener progressListener;
        
        public Builder channels(int channels) {
            this.channels = channels;
            return this;
        }
        
        public Builder frequency(int frequency) {
            this.frequency = frequency;
            return this;
        }
        
        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }
        
        public Builder path(Path path) {
            this.path = path;
            return this;
        }
        
        public Builder bufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }
        
        public Builder progressListener(ProgressListener listener) {
            this.progressListener = listener;
            return this;
        }
        
        public Builder mono() {
            this.channels = 1;
            return this;
        }
        
        public Builder stereo() {
            this.channels = 2;
            return this;
        }
        
        public Builder cdQuality() {
            this.frequency = 44100;
            this.channels = 2;
            return this;
        }
        
        /**
         * Build the WaveFileObuffer.
         * @return configured WaveFileObuffer instance
         */
        public WaveFileObuffer build() {
            WaveFileObuffer obuffer;
            
            if (path != null) {
                obuffer = new WaveFileObuffer(channels, frequency, path, bufferSize);
            } else if (filename != null) {
                obuffer = new WaveFileObuffer(channels, frequency, filename, bufferSize);
            } else {
                throw new IllegalStateException("Filename or path must be set");
            }
            
            if (progressListener != null) {
                obuffer.setProgressListener(progressListener);
            }
            
            return obuffer;
        }
    }
}