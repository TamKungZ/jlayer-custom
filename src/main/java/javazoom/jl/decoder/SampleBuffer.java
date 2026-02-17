/*
 * 11/19/04     1.0 moved to LGPL.
 *
 * 12/12/99  Initial Version based on FileObuffer.    mdm@techie.com.
 *
 * FileObuffer:
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

package javazoom.jl.decoder;

import java.util.Arrays;

/**
 * The {@code SampleBuffer} class implements an output buffer that provides
 * storage for a fixed-size block of PCM audio samples.
 * <p>
 * This is the primary buffer implementation used by the decoder. It stores
 * decoded samples in memory and provides access to them for playback or
 * further processing.
 * </p>
 * 
 * <p><b>Sample Layout:</b></p>
 * <p>
 * Samples are stored in interleaved format for stereo:
 * <pre>
 * Mono:   [L0, L1, L2, L3, ...]
 * Stereo: [L0, R0, L1, R1, L2, R2, ...]
 * </pre>
 * </p>
 * 
 * <p><b>Thread Safety:</b></p>
 * <p>
 * This class is not thread-safe. External synchronization is required
 * if accessed from multiple threads.
 * </p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create buffer for 44.1kHz stereo
 * SampleBuffer buffer = new SampleBuffer(44100, 2);
 * 
 * // Decoder fills the buffer
 * decoder.decodeFrame(header, stream, buffer);
 * 
 * // Get the samples
 * short[] samples = buffer.getBuffer();
 * int length = buffer.getBufferLength();
 * 
 * // Process samples
 * for (int i = 0; i < length; i++) {
 *     processSample(samples[i]);
 * }
 * 
 * // Clear for next frame
 * buffer.clearBuffer();
 * }</pre>
 *
 * @author MDM
 * @version 2.0
 * @since 0.0.7
 */
public class SampleBuffer extends Obuffer {

    /**
     * Internal buffer storing PCM samples.
     * Array size is OBUFFERSIZE (2304 samples max).
     */
    private final short[] buffer;
    
    /**
     * Write positions for each channel.
     * For stereo: bufferP[0] = left position, bufferP[1] = right position.
     * For mono: only bufferP[0] is used.
     */
    private final int[] bufferP;
    
    /**
     * Number of audio channels (1 = mono, 2 = stereo).
     */
    private final int channels;
    
    /**
     * Sample frequency in Hz (e.g., 44100, 48000).
     */
    private final int frequency;

    /**
     * Constructs a new SampleBuffer.
     * <p>
     * <b>Note:</b> For backward compatibility, no validation is performed.
     * Caller should ensure valid parameters.
     * </p>
     * 
     * @param sample_frequency the sample rate in Hz (e.g., 44100)
     * @param number_of_channels the number of channels (1 or 2)
     */
    public SampleBuffer(int sample_frequency, int number_of_channels) {
        buffer = new short[OBUFFERSIZE];
        bufferP = new int[MAXCHANNELS];
        channels = number_of_channels;
        frequency = sample_frequency;

        // Initialize write positions
        // For stereo: left starts at 0, right at 1
        // For mono: only position 0 is used
        for (int i = 0; i < number_of_channels; ++i) {
            bufferP[i] = i;
        }
    }

    /**
     * Gets the number of audio channels.
     * 
     * @return 1 for mono, 2 for stereo
     */
    public int getChannelCount() {
        return this.channels;
    }

    /**
     * Gets the sample frequency.
     * 
     * @return sample rate in Hz
     */
    public int getSampleFrequency() {
        return this.frequency;
    }

    /**
     * Gets the internal sample buffer.
     * <p>
     * <b>Warning:</b> This returns a direct reference to the internal buffer.
     * Modifications will affect the buffer's contents. Use {@link #getBufferLength()}
     * to determine how many samples are valid.
     * </p>
     * 
     * @return the internal buffer array
     */
    public short[] getBuffer() {
        return this.buffer;
    }
    
    /**
     * Gets a copy of the buffered samples.
     * <p>
     * This method creates a new array containing only the valid samples,
     * which is safer than {@link #getBuffer()} but involves memory allocation.
     * </p>
     * 
     * @return a new array containing the buffered samples
     * @since 2.0
     */
    public short[] getBufferCopy() {
        int length = getBufferLength();
        return Arrays.copyOf(buffer, length);
    }

    /**
     * Gets the number of valid samples in the buffer.
     * <p>
     * For stereo buffers, this is the total number of samples across both channels
     * (so it will be even). For mono, it's the number of samples in the single channel.
     * </p>
     * 
     * @return number of valid samples (not sample frames)
     */
    public int getBufferLength() {
        return bufferP[0];
    }
    
    /**
     * Gets the number of complete sample frames in the buffer.
     * <p>
     * A sample frame is one sample for each channel.
     * For stereo, this is getBufferLength() / 2.
     * For mono, this equals getBufferLength().
     * </p>
     * 
     * @return number of complete sample frames
     * @since 2.0
     */
    public int getSampleFrameCount() {
        return getBufferLength() / channels;
    }
    
    /**
     * Checks if the buffer contains any samples.
     * 
     * @return true if buffer has samples, false if empty
     * @since 2.0
     */
    public boolean hasData() {
        return getBufferLength() > 0;
    }
    
    /**
     * Checks if the buffer is full.
     * 
     * @return true if buffer cannot accept more samples
     * @since 2.0
     */
    public boolean isFull() {
        return bufferP[0] >= OBUFFERSIZE;
    }
    
    /**
     * Gets the remaining capacity in samples.
     * 
     * @return number of samples that can still be appended
     * @since 2.0
     */
    public int getRemainingCapacity() {
        return OBUFFERSIZE - bufferP[0];
    }

    /**
     * Appends a 16-bit PCM sample to the specified channel.
     * <p>
     * <b>Note:</b> This method does not validate inputs for performance.
     * Ensure channel is valid (0 or 1) and buffer has space.
     * </p>
     * 
     * @param channel the channel number (0 = left, 1 = right)
     * @param value the PCM sample value
     */
    @Override
    public void append(int channel, short value) {
        int pos = bufferP[channel];
        if (pos >= OBUFFERSIZE) {
            // Buffer full — drop samples to avoid ArrayIndexOutOfBounds
            return;
        }
        buffer[pos] = value;
        // advance write position by interleave step (channels)
        pos += channels;
        if (pos > OBUFFERSIZE) pos = OBUFFERSIZE; // clamp
        bufferP[channel] = pos;
    }

    /**
     * Appends 32 samples from a float array to the specified channel.
     * <p>
     * This is an optimized version that directly converts and clips
     * the samples without intermediate method calls.
     * </p>
     * <p>
     * <b>Note:</b> This method assumes array has at least 32 elements.
     * No validation is performed for performance.
     * </p>
     * 
     * @param channel the channel number (0 = left, 1 = right)
     * @param f array of 32 float samples
     */
    @Override
    public void appendSamples(int channel, float[] f) {
        int pos = bufferP[channel];

        // Optimized loop: convert, clip, and store
        // Stop early if buffer is full to avoid ArrayIndexOutOfBounds
        for (int i = 0; i < 32; i++) {
            if (pos >= OBUFFERSIZE) {
                break;
            }
            float fs = f[i];
            // Inline clipping - matches original behavior exactly
            if (fs > 32767.0f) {
                fs = 32767.0f;
            } else if (fs < -32767.0f) {
                fs = -32767.0f;
            }
            buffer[pos] = (short) fs;
            pos += channels;
        }

        if (pos > OBUFFERSIZE) pos = OBUFFERSIZE;
        bufferP[channel] = pos;
    }

    /**
     * Writes the samples to the output.
     * <p>
     * For SampleBuffer, this is a no-op since samples are already in memory.
     * Subclasses that write to files or hardware should override this.
     * </p>
     * 
     * @param val unused parameter (for compatibility)
     */
    @Override
    public void writeBuffer(int val) {
        // No-op: samples are already in the buffer
    }

    /**
     * Closes the buffer.
     * <p>
     * For SampleBuffer, this is a no-op. The buffer can still be used after calling this.
     * </p>
     */
    @Override
    public void close() {
        // No-op: nothing to close
    }

    /**
     * Clears all buffered samples and resets write positions.
     * <p>
     * After calling this method, {@link #getBufferLength()} will return 0
     * and the buffer is ready to receive new samples.
     * </p>
     * <p>
     * <b>Note:</b> This does not zero out the buffer array for performance reasons.
     * Old sample data remains in memory but is marked as invalid.
     * </p>
     */
    @Override
    public void clearBuffer() {
        for (int i = 0; i < channels; ++i) {
            bufferP[i] = i;
        }
    }
    
    /**
     * Clears the buffer and zeroes out all sample data.
     * <p>
     * This is more thorough than {@link #clearBuffer()} but slower.
     * Use this when security or deterministic behavior is required.
     * </p>
     * 
     * @since 2.0
     */
    public void clearBufferAndZero() {
        Arrays.fill(buffer, (short) 0);
        clearBuffer();
    }

    /**
     * Notifies the buffer that playback has stopped.
     * <p>
     * For SampleBuffer, this is a no-op. Subclasses that perform I/O
     * should override this to stop blocking operations.
     * </p>
     */
    @Override
    public void setStopFlag() {
        // No-op: no blocking operations to stop
    }
    
    /**
     * Resets the buffer to a clean state, equivalent to creating a new instance.
     * <p>
     * This clears all samples and resets write positions.
     * </p>
     * 
     * @since 2.0
     */
    public void reset() {
        clearBuffer();
    }
    
    /**
     * Returns a string representation of the buffer state.
     * 
     * @return string describing buffer parameters and state
     * @since 2.0
     */
    @Override
    public String toString() {
        return String.format(
            "SampleBuffer[frequency=%dHz, channels=%d, samples=%d/%d, frames=%d]",
            frequency, channels, getBufferLength(), OBUFFERSIZE, 
            getSampleFrameCount()
        );
    }
}