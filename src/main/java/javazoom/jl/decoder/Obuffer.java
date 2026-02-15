/*
 * 11/19/04  1.0 moved to LGPL.
 * 12/12/99  Added appendSamples() method for efficiency. MDM.
 * 15/02/99 ,Java Conversion by E.B ,ebsp@iname.com, JavaLayer
 *
 *   Declarations for output buffer, includes operating system
 *   implementation of the virtual Obuffer. Optional routines
 *   enabling seeks and stops added by Jeff Tsay.
 *
 *  @(#) obuffer.h 1.8, last edit: 6/15/94 16:51:56
 *  @(#) Copyright (C) 1993, 1994 Tobias Bading (bading@cs.tu-berlin.de)
 *  @(#) Berlin University of Technology
 *
 *  Idea and first implementation for u-law output with fast downsampling by
 *  Jim Boucher (jboucher@flash.bu.edu)
 *
 *  LinuxObuffer class written by
 *  Louis P. Kruger (lpkruger@phoenix.princeton.edu)
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
 * Abstract base class for audio output buffers.
 * <p>
 * This class provides the interface for receiving decoded PCM audio samples
 * from the MPEG audio decoder. Implementations can write to various outputs
 * such as audio hardware, files, or memory buffers.
 * </p>
 * 
 * <p><b>Thread Safety:</b></p>
 * <p>
 * Implementations of this class are not required to be thread-safe.
 * External synchronization should be used if the buffer is accessed
 * from multiple threads.
 * </p>
 * 
 * <p><b>Sample Format:</b></p>
 * <ul>
 *   <li>Samples are 16-bit signed PCM</li>
 *   <li>Range: -32768 to 32767</li>
 *   <li>Samples outside this range are clipped</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create custom implementation
 * Obuffer buffer = new MyCustomBuffer();
 * 
 * // Append single samples
 * buffer.append(0, (short) 1000);  // Left channel
 * buffer.append(1, (short) -500);  // Right channel
 * 
 * // Or append batch of samples (more efficient)
 * float[] samples = new float[32];
 * buffer.appendSamples(0, samples);
 * 
 * // Write when ready
 * buffer.writeBuffer(1);
 * 
 * // Clean up
 * buffer.close();
 * }</pre>
 *
 * @author Tobias Bading
 * @author Louis P. Kruger
 * @author MDM
 * @version 2.0
 * @see SampleBuffer
 * @since 0.0
 */
public abstract class Obuffer {

    /**
     * Maximum buffer size: 2 * 1152 samples per frame.
     * <p>
     * MPEG audio frames contain up to 1152 samples per channel.
     * With stereo (2 channels), this gives a maximum of 2304 samples.
     * </p>
     */
    public static final int OBUFFERSIZE = 2 * 1152;
    
    /**
     * Maximum number of channels supported (stereo).
     */
    public static final int MAXCHANNELS = 2;

    /**
     * Minimum valid PCM sample value.
     * 
     * @since 2.0
     */
    public static final short MIN_SAMPLE_VALUE = -32768;
    
    /**
     * Maximum valid PCM sample value.
     * 
     * @since 2.0
     */
    public static final short MAX_SAMPLE_VALUE = 32767;

    /**
     * Default constructor.
     */
    protected Obuffer() {
    }

    /**
     * Appends a single 16-bit PCM sample to the specified channel.
     * <p>
     * This method is called by the decoder for each decoded sample.
     * Samples are interleaved by channel in stereo mode.
     * </p>
     * 
     * @param channel the channel number (0 = left, 1 = right)
     * @param value the PCM sample value (range: -32768 to 32767)
     * @throws IllegalArgumentException if channel is invalid
     */
    public abstract void append(int channel, short value);

    /**
     * Appends 32 PCM samples from a float array to the specified channel.
     * <p>
     * This method is more efficient than calling {@link #append(int, short)}
     * 32 times. Float values are automatically clipped to the valid range
     * [-32768, 32767] and converted to shorts.
     * </p>
     * 
     * <p><b>Default Implementation:</b></p>
     * <p>
     * The default implementation clips each sample and calls {@link #append(int, short)}.
     * Subclasses may override for better performance.
     * </p>
     * 
     * @param channel the channel number (0 = left, 1 = right)
     * @param f array of 32 float samples to append
     * @throws IllegalArgumentException if channel is invalid or array length != 32
     */
    public void appendSamples(int channel, float[] f) {
        if (f == null || f.length < 32) {
            throw new IllegalArgumentException("Sample array must contain at least 32 elements");
        }
        
        for (int i = 0; i < 32; i++) {
            short s = clip(f[i]);
            append(channel, s);
        }
    }

    /**
     * Clips a floating-point sample to valid 16-bit PCM range.
     * <p>
     * Values above 32767.0 are clipped to 32767.
     * Values below -32768.0 are clipped to -32768.
     * </p>
     * 
     * @param sample the sample value to clip
     * @return clipped short value in range [-32768, 32767]
     */
    protected final short clip(float sample) {
        if (sample > 32767.0f) {
            return MAX_SAMPLE_VALUE;
        } else if (sample < -32768.0f) {
            return MIN_SAMPLE_VALUE;
        } else {
            return (short) sample;
        }
    }
    
    /**
     * Clips an integer sample to valid 16-bit PCM range.
     * 
     * @param sample the sample value to clip
     * @return clipped short value in range [-32768, 32767]
     * @since 2.0
     */
    protected final short clip(int sample) {
        if (sample > MAX_SAMPLE_VALUE) {
            return MAX_SAMPLE_VALUE;
        } else if (sample < MIN_SAMPLE_VALUE) {
            return MIN_SAMPLE_VALUE;
        } else {
            return (short) sample;
        }
    }

    /**
     * Writes buffered samples to the output destination.
     * <p>
     * This method is called by the decoder after a complete frame
     * has been decoded. Implementations should write all buffered
     * samples to their output destination (file, audio hardware, etc.).
     * </p>
     * 
     * @param val channel flags or sample count (implementation-specific)
     */
    public abstract void writeBuffer(int val);

    /**
     * Closes the output buffer and releases any associated resources.
     * <p>
     * After this method is called, no more samples should be appended.
     * This method should be idempotent (safe to call multiple times).
     * </p>
     */
    public abstract void close();

    /**
     * Clears all buffered data.
     * <p>
     * This method is used when seeking to a new position in the stream.
     * All pending samples are discarded and the buffer is reset to
     * its initial state.
     * </p>
     */
    public abstract void clearBuffer();

    /**
     * Notifies the buffer that playback has been stopped by the user.
     * <p>
     * This allows the buffer to perform any necessary cleanup or
     * to stop blocking operations. The buffer may be reused after
     * this call by calling {@link #clearBuffer()}.
     * </p>
     */
    public abstract void setStopFlag();
    
    /**
     * Checks if the specified channel number is valid.
     * 
     * @param channel the channel number to validate
     * @return true if channel is valid (0 or 1)
     * @since 2.0
     */
    protected final boolean isValidChannel(int channel) {
        return channel >= 0 && channel < MAXCHANNELS;
    }
    
    /**
     * Validates a channel number and throws an exception if invalid.
     * 
     * @param channel the channel number to validate
     * @throws IllegalArgumentException if channel is invalid
     * @since 2.0
     */
    protected final void validateChannel(int channel) {
        if (!isValidChannel(channel)) {
            throw new IllegalArgumentException(
                "Invalid channel: " + channel + " (must be 0-" + (MAXCHANNELS - 1) + ")"
            );
        }
    }
    
    /**
     * Gets the maximum buffer size.
     * 
     * @return the maximum number of samples that can be buffered
     * @since 2.0
     */
    public int getMaxBufferSize() {
        return OBUFFERSIZE;
    }
    
    /**
     * Gets the number of supported channels.
     * 
     * @return the maximum number of channels (2 for stereo)
     * @since 2.0
     */
    public int getMaxChannels() {
        return MAXCHANNELS;
    }
}