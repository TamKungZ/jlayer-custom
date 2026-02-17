package javazoom.jl.decoder;

/**
 * <p>
 * Bit reservoir implementation used by MPEG Layer III decoder.
 * </p>
 *
 * <p>
 * This implementation stores each bit as an individual {@code int}
 * entry in a circular buffer. A non-zero value represents bit {@code 1},
 * and zero represents bit {@code 0}.
 * </p>
 *
 * <p>
 * This class is <b>not thread-safe</b>.
 * </p>
 *
 * <h2>Compatibility</h2>
 * <ul>
 *   <li>All original public methods are preserved.</li>
 *   <li>Behavior remains identical to legacy JLayer implementation.</li>
 *   <li>Additional safety and utility APIs were added.</li>
 * </ul>
 *
 * <h2>Performance Notes</h2>
 * <p>
 * Buffer size must remain a power-of-two for masking optimization.
 * </p>
 *
 * @author JLayer (original)
 * @author Modernized version
 * @since 1.0
 */
final class BitReserve {

    /** Internal buffer size (must be power-of-two). */
    private static final int BUFSIZE = 4096 * 8;

    /** Mask used for fast modulo operations. */
    private static final int BUFSIZE_MASK = BUFSIZE - 1;

    /** Write offset (bit position). */
    private int offset;

    /** Total number of bits read. */
    private int totbit;

    /** Read pointer index. */
    private int bufByteIdx;

    /** Circular buffer storing bits as integers. */
    private final int[] buf = new int[BUFSIZE];

    /**
     * Creates a new empty BitReserve.
     */
    BitReserve() {
        clear();
    }

    /**
     * Returns total number of bits consumed so far.
     *
     * @return total consumed bit count
     */
    public int hsstell() {
        return totbit;
    }

    /**
     * Reads {@code n} bits from the reservoir.
     *
     * @param n number of bits to read
     * @return integer value composed of the read bits
     */
    public int hgetbits(int n) {
        if (n <= 0) return 0;

        totbit += n;

        int val = 0;
        int pos = bufByteIdx;

        for (int i = 0; i < n; i++) {
            val = (val << 1) | (buf[pos] != 0 ? 1 : 0);
            pos = (pos + 1) & BUFSIZE_MASK;
        }

        bufByteIdx = pos;
        return val;
    }

    /**
     * Reads a single bit.
     *
     * @return {@code 1} if bit set, {@code 0} otherwise
     */
    public int hget1bit() {
        totbit++;
        int val = buf[bufByteIdx] != 0 ? 1 : 0;
        bufByteIdx = (bufByteIdx + 1) & BUFSIZE_MASK;
        return val;
    }

    /**
     * Writes 8 bits (one byte) into reservoir.
     *
     * @param val byte value (only lowest 8 bits used)
     */
    public void hputbuf(int val) {
        int ofs = offset;

        buf[ofs] = val & 0x80; ofs = (ofs + 1) & BUFSIZE_MASK;
        buf[ofs] = val & 0x40; ofs = (ofs + 1) & BUFSIZE_MASK;
        buf[ofs] = val & 0x20; ofs = (ofs + 1) & BUFSIZE_MASK;
        buf[ofs] = val & 0x10; ofs = (ofs + 1) & BUFSIZE_MASK;
        buf[ofs] = val & 0x08; ofs = (ofs + 1) & BUFSIZE_MASK;
        buf[ofs] = val & 0x04; ofs = (ofs + 1) & BUFSIZE_MASK;
        buf[ofs] = val & 0x02; ofs = (ofs + 1) & BUFSIZE_MASK;
        buf[ofs] = val & 0x01; ofs = (ofs + 1) & BUFSIZE_MASK;

        offset = ofs;
    }

    /**
     * Rewinds {@code n} bits.
     *
     * @param n number of bits to rewind
     */
    public void rewindNBits(int n) {
        if (n <= 0) return;
        totbit -= n;
        bufByteIdx = (bufByteIdx - n) & BUFSIZE_MASK;
    }

    /**
     * Rewinds {@code n} bytes.
     *
     * @param n number of bytes to rewind
     */
    public void rewindNBytes(int n) {
        rewindNBits(n << 3);
    }

    /**
     * Returns number of bits currently available for reading.
     *
     * @return available bit count
     */
    public int available() {
        return (offset - bufByteIdx) & BUFSIZE_MASK;
    }

    /**
     * Returns buffer capacity in bits.
     *
     * @return total capacity
     */
    public int capacity() {
        return BUFSIZE;
    }

    /**
     * Clears buffer and resets internal state.
     */
    public void clear() {
        offset = 0;
        bufByteIdx = 0;
        totbit = 0;
    }

    /**
     * Peeks next {@code n} bits without advancing read pointer.
     *
     * @param n number of bits
     * @return bit value
     */
    public int peekBits(int n) {
        if (n <= 0) return 0;

        int val = 0;
        int pos = bufByteIdx;

        for (int i = 0; i < n; i++) {
            val = (val << 1) | (buf[pos] != 0 ? 1 : 0);
            pos = (pos + 1) & BUFSIZE_MASK;
        }
        return val;
    }

    /**
     * Peeks next bit without consuming it.
     *
     * @return next bit value
     */
    public int peek1Bit() {
        return buf[bufByteIdx] != 0 ? 1 : 0;
    }

    /**
     * Skips forward {@code n} bits.
     *
     * @param n number of bits to skip
     */
    public void skipBits(int n) {
        if (n <= 0) return;
        totbit += n;
        bufByteIdx = (bufByteIdx + n) & BUFSIZE_MASK;
    }

    /**
     * Returns current read pointer position.
     *
     * @return read index
     */
    public int getReadPosition() {
        return bufByteIdx;
    }

    /**
     * Returns current write pointer position.
     *
     * @return write index
     */
    public int getWritePosition() {
        return offset;
    }

    /**
     * Returns true if reservoir contains no readable bits.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return available() == 0;
    }

    /**
     * Returns true if reservoir is full.
     *
     * @return true if full
     */
    public boolean isFull() {
        return available() == BUFSIZE - 1;
    }
}
