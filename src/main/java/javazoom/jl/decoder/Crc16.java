/*
 * 11/19/04 : 1.0 moved to LGPL.
 *
 * 02/12/99 : Java Conversion by E.B , javalayer@javazoom.net
 *
 *  @(#) crc.h 1.5, last edit: 6/15/94 16:55:32
 *  @(#) Copyright (C) 1993, 1994 Tobias Bading (bading@cs.tu-berlin.de)
 *  @(#) Berlin University of Technology
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

import java.util.Objects;

/**
 * 16-Bit CRC checksum calculator for MPEG audio frame validation.
 * <p>
 * This class implements the CRC-16 algorithm using polynomial 0x8005
 * as specified in the MPEG audio standard. It provides both the original
 * API for backward compatibility and modern fluent API for new code.
 * </p>
 * 
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Each thread
 * should use its own instance.</p>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Traditional usage
 * Crc16 crc = new Crc16();
 * crc.addBits(0xABCD, 16);
 * short checksum = crc.checksum();
 * 
 * // Fluent API usage
 * short checksum = new Crc16()
 *     .update(0xABCD, 16)
 *     .update(0x1234, 16)
 *     .getValue();
 * 
 * // Verify checksum
 * boolean valid = Crc16.verify(data, length, expectedChecksum);
 * }</pre>
 *
 * @author Tobias Bading
 * @author E.B (Java conversion)
 * @version 2.0
 * @since 1.0
 */
public final class Crc16 {

    /**
     * The CRC-16 polynomial used in MPEG audio (0x8005).
     */
    private static final short POLYNOMIAL = (short) 0x8005;
    
    /**
     * Initial CRC value.
     */
    private static final short INITIAL_VALUE = (short) 0xFFFF;
    
    /**
     * Maximum valid bit length for a single operation.
     */
    private static final int MAX_BIT_LENGTH = 32;
    
    /**
     * Current CRC value.
     */
    private short crc;

    /**
     * Constructs a new CRC-16 calculator with initial value 0xFFFF.
     */
    public Crc16() {
        reset();
    }
    
    /**
     * Constructs a new CRC-16 calculator with a specific initial value.
     * 
     * @param initialValue the initial CRC value
     * @since 2.0
     */
    public Crc16(short initialValue) {
        this.crc = initialValue;
    }

    /**
     * Feeds a bit string to the CRC calculation.
     * <p>
     * Valid length range: {@code 0 < length <= 32}
     * </p>
     * 
     * @param bitString the bits to add to the CRC calculation
     * @param length the number of bits to process (1-32)
     * @throws IllegalArgumentException if length is out of valid range
     * 
     * @deprecated Use {@link #update(int, int)} for better naming consistency
     */
    @Deprecated
    public void addBits(int bitString, int length) {
        validateLength(length);
        updateInternal(bitString, length);
    }
    
    /**
     * Updates the CRC with the specified bits (modern API).
     * <p>
     * Valid length range: {@code 0 < length <= 32}
     * </p>
     * 
     * @param bitString the bits to add to the CRC calculation
     * @param length the number of bits to process (1-32)
     * @return this CRC instance for method chaining
     * @throws IllegalArgumentException if length is out of valid range
     * @since 2.0
     */
    public Crc16 update(int bitString, int length) {
        validateLength(length);
        updateInternal(bitString, length);
        return this;
    }
    
    /**
     * Updates the CRC with an entire byte.
     * 
     * @param b the byte to add to the CRC calculation
     * @return this CRC instance for method chaining
     * @since 2.0
     */
    public Crc16 update(byte b) {
        updateInternal(b & 0xFF, 8);
        return this;
    }
    
    /**
     * Updates the CRC with a byte array.
     * 
     * @param bytes the byte array to process
     * @return this CRC instance for method chaining
     * @throws NullPointerException if bytes is null
     * @since 2.0
     */
    public Crc16 update(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        return update(bytes, 0, bytes.length);
    }
    
    /**
     * Updates the CRC with a portion of a byte array.
     * 
     * @param bytes the byte array to process
     * @param offset the starting offset in the array
     * @param length the number of bytes to process
     * @return this CRC instance for method chaining
     * @throws NullPointerException if bytes is null
     * @throws IndexOutOfBoundsException if offset or length are invalid
     * @since 2.0
     */
    public Crc16 update(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        Objects.checkFromIndexSize(offset, length, bytes.length);
        
        for (int i = 0; i < length; i++) {
            updateInternal(bytes[offset + i] & 0xFF, 8);
        }
        return this;
    }

    /**
     * Returns the calculated checksum and resets for next calculation.
     * <p>
     * This is the original API method. After calling this method,
     * the CRC is reset to initial value 0xFFFF.
     * </p>
     * 
     * @return the calculated CRC-16 checksum
     */
    public short checksum() {
        short sum = crc;
        reset();
        return sum;
    }
    
    /**
     * Returns the current CRC value without resetting (modern API).
     * <p>
     * Unlike {@link #checksum()}, this method does NOT reset the CRC.
     * Use {@link #reset()} explicitly if needed.
     * </p>
     * 
     * @return the current CRC-16 value
     * @since 2.0
     */
    public short getValue() {
        return crc;
    }
    
    /**
     * Resets the CRC to initial value 0xFFFF.
     * 
     * @return this CRC instance for method chaining
     * @since 2.0
     */
    public Crc16 reset() {
        this.crc = INITIAL_VALUE;
        return this;
    }
    
    /**
     * Returns the current CRC value as an unsigned integer (0-65535).
     * 
     * @return the CRC value as unsigned int
     * @since 2.0
     */
    public int getValueAsInt() {
        return crc & 0xFFFF;
    }
    
    /**
     * Creates a copy of this CRC calculator with the same state.
     * 
     * @return a new Crc16 instance with the same CRC value
     * @since 2.0
     */
    public Crc16 copy() {
        return new Crc16(this.crc);
    }
    
    /**
     * Compares this CRC value with another CRC calculator.
     * 
     * @param other the other CRC calculator to compare
     * @return true if both have the same CRC value
     * @since 2.0
     */
    public boolean matches(Crc16 other) {
        return other != null && this.crc == other.crc;
    }
    
    /**
     * Compares this CRC value with a checksum value.
     * 
     * @param checksum the checksum to compare
     * @return true if the CRC matches the checksum
     * @since 2.0
     */
    public boolean matches(short checksum) {
        return this.crc == checksum;
    }

    /**
     * Internal CRC update implementation.
     * 
     * @param bitString the bits to process
     * @param length the number of bits
     */
    private void updateInternal(int bitString, int length) {
        int bitmask = 1 << (length - 1);
        do {
            if (((crc & 0x8000) == 0) ^ ((bitString & bitmask) == 0)) {
                crc <<= 1;
                crc ^= POLYNOMIAL;
            } else {
                crc <<= 1;
            }
        } while ((bitmask >>>= 1) != 0);
    }
    
    /**
     * Validates bit length parameter.
     * 
     * @param length the length to validate
     * @throws IllegalArgumentException if length is out of range
     */
    private void validateLength(int length) {
        if (length <= 0 || length > MAX_BIT_LENGTH) {
            throw new IllegalArgumentException(
                "Length must be between 1 and " + MAX_BIT_LENGTH + ", got: " + length);
        }
    }
    
    // Static utility methods
    
    /**
     * Calculates CRC-16 for a byte array.
     * 
     * @param data the data to calculate CRC for
     * @return the calculated CRC-16 checksum
     * @throws NullPointerException if data is null
     * @since 2.0
     */
    public static short calculate(byte[] data) {
        Objects.requireNonNull(data, "data cannot be null");
        return calculate(data, 0, data.length);
    }
    
    /**
     * Calculates CRC-16 for a portion of a byte array.
     * 
     * @param data the data array
     * @param offset the starting offset
     * @param length the number of bytes to process
     * @return the calculated CRC-16 checksum
     * @throws NullPointerException if data is null
     * @throws IndexOutOfBoundsException if offset or length are invalid
     * @since 2.0
     */
    public static short calculate(byte[] data, int offset, int length) {
        return new Crc16().update(data, offset, length).getValue();
    }
    
    /**
     * Verifies data against a CRC checksum.
     * 
     * @param data the data to verify
     * @param expectedChecksum the expected CRC value
     * @return true if the calculated CRC matches the expected value
     * @throws NullPointerException if data is null
     * @since 2.0
     */
    public static boolean verify(byte[] data, short expectedChecksum) {
        return calculate(data) == expectedChecksum;
    }
    
    /**
     * Verifies a portion of data against a CRC checksum.
     * 
     * @param data the data array
     * @param offset the starting offset
     * @param length the number of bytes to verify
     * @param expectedChecksum the expected CRC value
     * @return true if the calculated CRC matches the expected value
     * @throws NullPointerException if data is null
     * @throws IndexOutOfBoundsException if offset or length are invalid
     * @since 2.0
     */
    public static boolean verify(byte[] data, int offset, int length, short expectedChecksum) {
        return calculate(data, offset, length) == expectedChecksum;
    }
    
    @Override
    public String toString() {
        return String.format("Crc16[value=0x%04X]", getValueAsInt());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Crc16)) return false;
        Crc16 other = (Crc16) obj;
        return this.crc == other.crc;
    }
    
    @Override
    public int hashCode() {
        return Short.hashCode(crc);
    }
}