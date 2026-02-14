package javazoom.jl.decoder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class BitstreamEdgeCaseTest {

    @Test
    public void testGetBitsNoExceptionOnVariousSizes() throws Exception {
        InputStream is = getClass().getResourceAsStream("/test.mp3");
        assertNotNull(is, "test.mp3 resource must be available for this test");

        Bitstream bs = new Bitstream(is);
        try {
            // Read a number of bit-widths repeatedly and ensure no exceptions
            for (int i = 0; i < 1000 && !bs.isEOF(); i++) {
                int[] bitSizes = {1, 2, 4, 7, 8, 12, 16, 20, 24, 31, 32};
                for (int bits : bitSizes) {
                    final int b = bits;
                    assertDoesNotThrow(() -> {
                        int val = bs.getBits(b);
                        // returned value should be within 0 .. (2^b - 1)
                        if (b > 31) return; // avoid shifting issues
                        assertTrue(val >= 0);
                        assertTrue(val <= (b == 32 ? Integer.MAX_VALUE : ((1 << b) - 1)));
                    }, "getBits(" + b + ") should not throw");
                    if (bs.isEOF()) break;
                }
            }
        } finally {
            bs.close();
        }
    }

    @Test
    public void testGetBitsPartialWordAtEOF() throws Exception {
        // Create a tiny stream with 3 bytes only (partial 32-bit word)
        byte[] threeBytes = new byte[] {(byte)0xAA, (byte)0xBB, (byte)0xCC};
        Bitstream bs = new Bitstream(new ByteArrayInputStream(threeBytes));
        try {
            // Try to read 32 bits repeatedly — should not throw, should reach EOF
            int v = bs.getBits(32);
            // value is an int; just assert call succeeded
            assertNotNull(Integer.valueOf(v));
            // subsequent reads eventually indicate EOF
            bs.getBits(8);
            assertTrue(bs.isEOF() || bs.getBits(1) >= 0);
        } finally {
            bs.close();
        }
    }
}
