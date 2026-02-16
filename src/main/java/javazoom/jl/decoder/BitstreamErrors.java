/*
 * 11/19/04        1.0 moved to LGPL.
 * 11/17/04        INVALIDFRAME code added.    javalayer@javazoom.net
 * 12/12/99        Initial version.            mdm@techie.com
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
 * This interface describes all error codes that can be thrown
 * in <code>BistreamException</code>s.
 *
 * @author MDM        12/12/99
 * @see BitstreamException
 * @since 0.0.6
 */
public interface BitstreamErrors extends JavaLayerErrors {

    /**
     * An undeterminable error occurred.
     */
    int UNKNOWN_ERROR = BITSTREAM_ERROR + 0;

    /**
     * The header describes an unknown sample rate.
     */
    int UNKNOWN_SAMPLE_RATE = BITSTREAM_ERROR + 1;

    /**
     * A problem occurred reading from the stream.
     */
    int STREAM_ERROR = BITSTREAM_ERROR + 2;

    /**
     * The end of the stream was reached prematurely.
     */
    int UNEXPECTED_EOF = BITSTREAM_ERROR + 3;

    /**
     * The end of the stream was reached.
     */
    int STREAM_EOF = BITSTREAM_ERROR + 4;

    /**
     * Frame data are missing.
     */
    int INVALIDFRAME = BITSTREAM_ERROR + 5;

    /**
     *
     */
    int BITSTREAM_LAST = 0x1ff;

    /**
     * Additional, backward-compatible error codes for finer-grained
     * diagnostics. Values continue after existing codes to preserve
     * compatibility with existing callers.
     */
    int BUFFER_OVERFLOW = BITSTREAM_ERROR + 6;
    int INVALID_SYNC = BITSTREAM_ERROR + 7;
    int ID3V2_ERROR = BITSTREAM_ERROR + 8;
    int CRC_MISMATCH = BITSTREAM_ERROR + 9;

    /**
     * Return a human-readable message for a given bitstream error code.
     * This is a convenience helper to avoid duplicating error text across
     * callers. The method is static so existing implementations of this
     * interface are unaffected (backwards compatible).
     *
     * @param errorCode the bitstream error code
     * @return a short, human readable message describing the error
     * @since 1.0.5
     */
    static String getErrorMessage(int errorCode) {
        return switch (errorCode) {
            case UNKNOWN_ERROR -> "Undeterminable bitstream error";
            case UNKNOWN_SAMPLE_RATE -> "Unknown sample rate in header";
            case STREAM_ERROR -> "I/O error while reading the stream";
            case UNEXPECTED_EOF -> "Unexpected end of stream";
            case STREAM_EOF -> "End of stream reached";
            case INVALIDFRAME -> "Invalid or incomplete frame data";
            case BUFFER_OVERFLOW -> "Frame buffer overflow";
            case INVALID_SYNC -> "Invalid sync word found";
            case ID3V2_ERROR -> "ID3v2 tag parsing error";
            case CRC_MISMATCH -> "Frame CRC mismatch";
            default -> "Bitstream errorCode 0x" + Integer.toHexString(errorCode);
        };
    }

    /**
     * Check whether the given code is part of the bitstream error range.
     *
     * @param code error code to check
     * @return true if the code belongs to the BitstreamErrors range
     * @since 1.0.5
     */
    static boolean isBitstreamError(int code) {
        return code >= BITSTREAM_ERROR && code <= BITSTREAM_LAST;
    }
}
