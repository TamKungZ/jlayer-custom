/*
 * 11/19/04 1.0 moved to LGPL.
 * 12/12/99 Initial implementation.        mdm@techie.com.
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
 * Type-safe representation of supported output channel configurations.
 * <p>
 * This class provides constants for controlling which audio channels
 * should be decoded and output. It supports:
 * </p>
 * <ul>
 *   <li>Both channels (stereo)</li>
 *   <li>Left channel only</li>
 *   <li>Right channel only</li>
 *   <li>Downmix to mono</li>
 * </ul>
 * 
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Decode both channels (default)
 * OutputChannels channels = OutputChannels.BOTH;
 * 
 * // Decode only left channel
 * OutputChannels channels = OutputChannels.LEFT;
 * 
 * // Create from integer code
 * OutputChannels channels = OutputChannels.fromInt(OutputChannels.RIGHT_CHANNEL);
 * 
 * // Check configuration
 * if (channels.isStereo()) {
 *     System.out.println("Stereo output");
 * }
 * 
 * // Get channel count
 * int count = channels.getChannelCount();  // 1 or 2
 * }</pre>
 *
 * @author Mat McGowan
 * @version 2.0
 * @since 0.0.7
 */
public final class OutputChannels {

    /**
     * Flag to indicate output should include both channels (stereo).
     * <p>
     * This is the default mode for stereo streams.
     * </p>
     */
    public static final int BOTH_CHANNELS = 0;

    /**
     * Flag to indicate output should include the left channel only.
     * <p>
     * The right channel will be decoded but not output.
     * </p>
     */
    public static final int LEFT_CHANNEL = 1;

    /**
     * Flag to indicate output should include the right channel only.
     * <p>
     * The left channel will be decoded but not output.
     * </p>
     */
    public static final int RIGHT_CHANNEL = 2;

    /**
     * Flag to indicate output should be mono (downmix).
     * <p>
     * Both channels are decoded and mixed to mono output.
     * </p>
     */
    public static final int DOWNMIX_CHANNELS = 3;

    /**
     * Pre-defined instance for left channel output.
     */
    public static final OutputChannels LEFT = new OutputChannels(LEFT_CHANNEL);
    
    /**
     * Pre-defined instance for right channel output.
     */
    public static final OutputChannels RIGHT = new OutputChannels(RIGHT_CHANNEL);
    
    /**
     * Pre-defined instance for both channels (stereo) output.
     * This is the default for stereo streams.
     */
    public static final OutputChannels BOTH = new OutputChannels(BOTH_CHANNELS);
    
    /**
     * Pre-defined instance for downmix to mono output.
     */
    public static final OutputChannels DOWNMIX = new OutputChannels(DOWNMIX_CHANNELS);

    /**
     * Internal channel code.
     */
    private final int outputChannels;

    /**
     * Creates an {@code OutputChannels} instance from a channel code.
     * <p>
     * It's recommended to use the pre-defined constants ({@link #LEFT},
     * {@link #RIGHT}, {@link #BOTH}, {@link #DOWNMIX}) instead of this method.
     * </p>
     * 
     * @param code one of the channel code constants
     * @return the corresponding OutputChannels instance
     * @throws IllegalArgumentException if code is not valid
     */
    public static OutputChannels fromInt(int code) {
        return switch (code) {
            case LEFT_CHANNEL -> LEFT;
            case RIGHT_CHANNEL -> RIGHT;
            case BOTH_CHANNELS -> BOTH;
            case DOWNMIX_CHANNELS -> DOWNMIX;
            default -> throw new IllegalArgumentException("Invalid channel code: " + code);
        };
    }
    
    /**
     * Safely creates an {@code OutputChannels} instance from a channel code.
     * <p>
     * Returns {@link #BOTH} if the code is invalid instead of throwing an exception.
     * </p>
     * 
     * @param code channel code to convert
     * @param defaultValue the value to return if code is invalid
     * @return the OutputChannels instance, or defaultValue if code is invalid
     * @since 2.0
     */
    public static OutputChannels fromIntOrDefault(int code, OutputChannels defaultValue) {
        try {
            return fromInt(code);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
    
    /**
     * Checks if a channel code is valid.
     * 
     * @param code the code to check
     * @return true if code is valid (0-3)
     * @since 2.0
     */
    public static boolean isValidCode(int code) {
        return code >= 0 && code <= 3;
    }

    /**
     * Private constructor. Use pre-defined constants or {@link #fromInt(int)}.
     * 
     * @param channels the channel code
     * @throws IllegalArgumentException if channels is out of range
     */
    private OutputChannels(int channels) {
        if (channels < 0 || channels > 3) {
            throw new IllegalArgumentException("Invalid channel code: " + channels);
        }
        this.outputChannels = channels;
    }

    /**
     * Retrieves the code representing the desired output channels.
     * <p>
     * Will be one of:
     * </p>
     * <ul>
     *   <li>{@link #LEFT_CHANNEL}</li>
     *   <li>{@link #RIGHT_CHANNEL}</li>
     *   <li>{@link #BOTH_CHANNELS}</li>
     *   <li>{@link #DOWNMIX_CHANNELS}</li>
     * </ul>
     * 
     * @return the channel code represented by this instance
     */
    public int getChannelsOutputCode() {
        return outputChannels;
    }

    /**
     * Retrieves the number of output channels.
     * 
     * @return 2 for {@link #BOTH_CHANNELS}, 1 for all other modes
     */
    public int getChannelCount() {
        return (outputChannels == BOTH_CHANNELS) ? 2 : 1;
    }
    
    /**
     * Checks if this configuration outputs stereo (both channels).
     * 
     * @return true if BOTH_CHANNELS, false otherwise
     * @since 2.0
     */
    public boolean isStereo() {
        return outputChannels == BOTH_CHANNELS;
    }
    
    /**
     * Checks if this configuration outputs mono.
     * 
     * @return true if LEFT, RIGHT, or DOWNMIX
     * @since 2.0
     */
    public boolean isMono() {
        return !isStereo();
    }
    
    /**
     * Checks if this configuration is downmixed to mono.
     * 
     * @return true if DOWNMIX_CHANNELS
     * @since 2.0
     */
    public boolean isDownmix() {
        return outputChannels == DOWNMIX_CHANNELS;
    }
    
    /**
     * Checks if this configuration outputs only the left channel.
     * 
     * @return true if LEFT_CHANNEL
     * @since 2.0
     */
    public boolean isLeftOnly() {
        return outputChannels == LEFT_CHANNEL;
    }
    
    /**
     * Checks if this configuration outputs only the right channel.
     * 
     * @return true if RIGHT_CHANNEL
     * @since 2.0
     */
    public boolean isRightOnly() {
        return outputChannels == RIGHT_CHANNEL;
    }
    
    /**
     * Checks if the left channel should be decoded.
     * 
     * @return true unless RIGHT_CHANNEL
     * @since 2.0
     */
    public boolean shouldDecodeLeft() {
        return outputChannels != RIGHT_CHANNEL;
    }
    
    /**
     * Checks if the right channel should be decoded.
     * 
     * @return true unless LEFT_CHANNEL
     * @since 2.0
     */
    public boolean shouldDecodeRight() {
        return outputChannels != LEFT_CHANNEL;
    }

    /**
     * Compares this OutputChannels with another object for equality.
     * 
     * @param o object to compare with
     * @return true if o is an OutputChannels with the same code
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutputChannels)) return false;
        
        OutputChannels that = (OutputChannels) o;
        return outputChannels == that.outputChannels;
    }

    /**
     * Returns the hash code for this OutputChannels.
     * 
     * @return the channel code as hash code
     */
    @Override
    public int hashCode() {
        return outputChannels;
    }
    
    /**
     * Returns a string representation of this OutputChannels.
     * 
     * @return human-readable channel configuration name
     * @since 2.0
     */
    @Override
    public String toString() {
        return switch (outputChannels) {
            case LEFT_CHANNEL -> "LEFT";
            case RIGHT_CHANNEL -> "RIGHT";
            case BOTH_CHANNELS -> "BOTH";
            case DOWNMIX_CHANNELS -> "DOWNMIX";
            default -> "UNKNOWN(" + outputChannels + ")";
        };
    }
    
    /**
     * Returns a descriptive string for this configuration.
     * 
     * @return detailed description
     * @since 2.0
     */
    public String getDescription() {
        return switch (outputChannels) {
            case LEFT_CHANNEL -> "Left channel only";
            case RIGHT_CHANNEL -> "Right channel only";
            case BOTH_CHANNELS -> "Both channels (stereo)";
            case DOWNMIX_CHANNELS -> "Downmix to mono";
            default -> "Unknown configuration";
        };
    }
}