/*
 * 09/26/08     throw exception on subbband alloc error: Christopher G. Jennings (cjennings@acm.org)
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This interface provides constants describing the error codes used by the
 * Decoder to indicate errors during MPEG audio decoding.
 * <p>
 * Error codes are organized hierarchically, with the base {@code DECODER_ERROR}
 * value inherited from {@link JavaLayerErrors}. Specific error conditions are
 * offset from this base value.
 * </p>
 * 
 * <p><b>Error Code Ranges:</b></p>
 * <ul>
 *   <li>0x200-0x2FF: Decoder errors (general)</li>
 *   <li>0x300-0x3FF: Layer-specific errors</li>
 *   <li>0x400-0x4FF: Format/stream errors</li>
 *   <li>0x500-0x5FF: Resource/memory errors</li>
 * </ul>
 *
 * @author MDM
 * @author Enhanced by modernization
 * @version 2.0
 * @since 0.0.5
 * @see DecoderException
 * @see JavaLayerErrors
 */
public interface DecoderErrors extends JavaLayerErrors {

    // ========================================================================
    // General Decoder Errors (0x200-0x2FF)
    // ========================================================================

    /**
     * An undetermined decoder error occurred.
     * <p>
     * This is a catch-all error code when the specific cause cannot be identified.
     * </p>
     */
    int UNKNOWN_ERROR = DECODER_ERROR + 0x00;

    /**
     * Layer not supported by the decoder.
     * <p>
     * The MPEG audio layer (I, II, or III) specified in the frame header
     * is not supported by this decoder implementation.
     * </p>
     * 
     * @see #INVALID_LAYER
     */
    int UNSUPPORTED_LAYER = DECODER_ERROR + 0x01;

    /**
     * Illegal allocation in subband layer.
     * <p>
     * Indicates a corrupt or malformed stream where the subband allocation
     * table contains invalid values. This typically means the audio data
     * is corrupted or not a valid MPEG audio stream.
     * </p>
     */
    int ILLEGAL_SUBBAND_ALLOCATION = DECODER_ERROR + 0x02;

    // ========================================================================
    // Layer-Specific Errors (0x210-0x2FF)
    // ========================================================================

    /**
     * Invalid layer value in frame header.
     * <p>
     * The layer field contains a reserved or invalid value (not 1, 2, or 3).
     * </p>
     * 
     * @since 2.0
     */
    int INVALID_LAYER = DECODER_ERROR + 0x10;

    /**
     * Layer I decoding error.
     * 
     * @since 2.0
     */
    int LAYER_I_ERROR = DECODER_ERROR + 0x11;

    /**
     * Layer II decoding error.
     * 
     * @since 2.0
     */
    int LAYER_II_ERROR = DECODER_ERROR + 0x12;

    /**
     * Layer III (MP3) decoding error.
     * 
     * @since 2.0
     */
    int LAYER_III_ERROR = DECODER_ERROR + 0x13;

    /**
     * Huffman decoding error in Layer III.
     * <p>
     * Invalid Huffman code encountered during Layer III decoding.
     * </p>
     * 
     * @since 2.0
     */
    int HUFFMAN_DECODE_ERROR = DECODER_ERROR + 0x14;

    /**
     * Scale factor error.
     * <p>
     * Invalid or out-of-range scale factor value.
     * </p>
     * 
     * @since 2.0
     */
    int INVALID_SCALE_FACTOR = DECODER_ERROR + 0x15;

    // ========================================================================
    // Format/Stream Errors (0x220-0x2FF)
    // ========================================================================

    /**
     * Invalid or corrupted frame header.
     * 
     * @since 2.0
     */
    int INVALID_FRAME_HEADER = DECODER_ERROR + 0x20;

    /**
     * CRC check failed.
     * <p>
     * The frame's CRC checksum does not match the calculated value,
     * indicating data corruption.
     * </p>
     * 
     * @since 2.0
     */
    int CRC_MISMATCH = DECODER_ERROR + 0x21;

    /**
     * Invalid bitrate specified in header.
     * 
     * @since 2.0
     */
    int INVALID_BITRATE = DECODER_ERROR + 0x22;

    /**
     * Invalid sampling frequency in header.
     * 
     * @since 2.0
     */
    int INVALID_SAMPLE_RATE = DECODER_ERROR + 0x23;

    /**
     * Invalid channel mode configuration.
     * 
     * @since 2.0
     */
    int INVALID_CHANNEL_MODE = DECODER_ERROR + 0x24;

    /**
     * Main data begin offset is invalid.
     * <p>
     * In Layer III, the main_data_begin pointer references data outside
     * the valid range (bit reservoir error).
     * </p>
     * 
     * @since 2.0
     */
    int INVALID_MAIN_DATA_BEGIN = DECODER_ERROR + 0x25;

    /**
     * Side information is corrupted or invalid.
     * 
     * @since 2.0
     */
    int INVALID_SIDE_INFO = DECODER_ERROR + 0x26;

    // ========================================================================
    // Resource/Memory Errors (0x230-0x2FF)
    // ========================================================================

    /**
     * Output buffer is full or unavailable.
     * 
     * @since 2.0
     */
    int OUTPUT_BUFFER_FULL = DECODER_ERROR + 0x30;

    /**
     * Insufficient memory for decoding operation.
     * 
     * @since 2.0
     */
    int OUT_OF_MEMORY = DECODER_ERROR + 0x31;

    /**
     * Decoder not properly initialized.
     * <p>
     * Attempted to decode before calling initialization methods.
     * </p>
     * 
     * @since 2.0
     */
    int NOT_INITIALIZED = DECODER_ERROR + 0x32;

    /**
     * Invalid decoder state for the requested operation.
     * 
     * @since 2.0
     */
    int INVALID_STATE = DECODER_ERROR + 0x33;

    // ========================================================================
    // Utility Methods
    // ========================================================================

    /**
     * Map of error codes to human-readable descriptions.
     * 
     * @since 2.0
     */
    Map<Integer, String> ERROR_DESCRIPTIONS = Collections.unmodifiableMap(new HashMap<>() {{
        put(UNKNOWN_ERROR, "Unknown decoder error");
        put(UNSUPPORTED_LAYER, "MPEG layer not supported by decoder");
        put(ILLEGAL_SUBBAND_ALLOCATION, "Illegal subband allocation - corrupt stream");
        put(INVALID_LAYER, "Invalid layer value in frame header");
        put(LAYER_I_ERROR, "Layer I decoding error");
        put(LAYER_II_ERROR, "Layer II decoding error");
        put(LAYER_III_ERROR, "Layer III (MP3) decoding error");
        put(HUFFMAN_DECODE_ERROR, "Huffman decoding error in Layer III");
        put(INVALID_SCALE_FACTOR, "Invalid scale factor value");
        put(INVALID_FRAME_HEADER, "Invalid or corrupted frame header");
        put(CRC_MISMATCH, "CRC check failed - data corruption detected");
        put(INVALID_BITRATE, "Invalid bitrate in frame header");
        put(INVALID_SAMPLE_RATE, "Invalid sampling frequency in header");
        put(INVALID_CHANNEL_MODE, "Invalid channel mode configuration");
        put(INVALID_MAIN_DATA_BEGIN, "Invalid main_data_begin offset (bit reservoir error)");
        put(INVALID_SIDE_INFO, "Corrupted or invalid side information");
        put(OUTPUT_BUFFER_FULL, "Output buffer full or unavailable");
        put(OUT_OF_MEMORY, "Insufficient memory for decoding");
        put(NOT_INITIALIZED, "Decoder not initialized");
        put(INVALID_STATE, "Invalid decoder state");
    }});

    /**
     * Gets a human-readable description for an error code.
     * 
     * @param errorCode the error code
     * @return the description, or a default message if unknown
     * @since 2.0
     */
    static String getErrorDescription(int errorCode) {
        return ERROR_DESCRIPTIONS.getOrDefault(errorCode, 
            "Unknown error code: 0x" + Integer.toHexString(errorCode));
    }

    /**
     * Gets an optional description for an error code.
     * 
     * @param errorCode the error code
     * @return Optional containing the description, or empty if unknown
     * @since 2.0
     */
    static Optional<String> getErrorDescriptionOptional(int errorCode) {
        return Optional.ofNullable(ERROR_DESCRIPTIONS.get(errorCode));
    }

    /**
     * Checks if an error code is a decoder error.
     * 
     * @param errorCode the error code to check
     * @return true if the code is in the decoder error range
     * @since 2.0
     */
    static boolean isDecoderError(int errorCode) {
        return errorCode >= DECODER_ERROR && errorCode < (DECODER_ERROR + 0x100);
    }

    /**
     * Checks if an error code indicates a layer-specific error.
     * 
     * @param errorCode the error code to check
     * @return true if the code is a layer-specific error
     * @since 2.0
     */
    static boolean isLayerError(int errorCode) {
        return errorCode >= (DECODER_ERROR + 0x10) && errorCode < (DECODER_ERROR + 0x20);
    }

    /**
     * Checks if an error code indicates a format/stream error.
     * 
     * @param errorCode the error code to check
     * @return true if the code is a format/stream error
     * @since 2.0
     */
    static boolean isFormatError(int errorCode) {
        return errorCode >= (DECODER_ERROR + 0x20) && errorCode < (DECODER_ERROR + 0x30);
    }

    /**
     * Checks if an error code indicates a resource/memory error.
     * 
     * @param errorCode the error code to check
     * @return true if the code is a resource error
     * @since 2.0
     */
    static boolean isResourceError(int errorCode) {
        return errorCode >= (DECODER_ERROR + 0x30) && errorCode < (DECODER_ERROR + 0x40);
    }

    /**
     * Gets the error category name.
     * 
     * @param errorCode the error code
     * @return the category name
     * @since 2.0
     */
    static String getErrorCategory(int errorCode) {
        if (isLayerError(errorCode)) return "Layer Error";
        if (isFormatError(errorCode)) return "Format Error";
        if (isResourceError(errorCode)) return "Resource Error";
        if (isDecoderError(errorCode)) return "Decoder Error";
        return "Unknown Category";
    }
}