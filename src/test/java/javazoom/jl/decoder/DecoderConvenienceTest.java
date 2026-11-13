package javazoom.jl.decoder;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecoderConvenienceTest {

    private static InputStream openTestMp3() {
        InputStream in = DecoderConvenienceTest.class.getClassLoader().getResourceAsStream("test.mp3");
        assertNotNull(in, "test.mp3 not found");
        return in;
    }

    @Test
    @DisplayName("decodeNextFrameToPCM() returns 16-bit PCM for the first frame")
    void decodeNextFrameToPcm() throws Exception {
        try (InputStream in = openTestMp3()) {
            Bitstream bs = new Bitstream(in);
            byte[] pcm = new Decoder().decodeNextFrameToPCM(bs);
            assertNotNull(pcm);
            assertTrue(pcm.length > 0);
            assertEquals(0, pcm.length % 2);
        }
    }

    @Test
    @DisplayName("Little-endian and big-endian PCM differ only by byte order")
    void endianness() throws Exception {
        byte[] le;
        byte[] be;
        try (InputStream in = openTestMp3()) {
            le = new Decoder().decodeNextFrameToPCM(new Bitstream(in), true);
        }
        try (InputStream in = openTestMp3()) {
            be = new Decoder().decodeNextFrameToPCM(new Bitstream(in), false);
        }
        assertEquals(le.length, be.length);
        for (int i = 0; i + 1 < le.length; i += 2) {
            assertEquals(le[i], be[i + 1]);
            assertEquals(le[i + 1], be[i]);
        }
    }

    @Test
    @DisplayName("decodeNextFrameToPCM() returns null at end of stream")
    void endOfStream() throws Exception {
        try (InputStream in = openTestMp3()) {
            Bitstream bs = new Bitstream(in);
            Decoder decoder = new Decoder();
            int frames = 0;
            while (decoder.decodeNextFrameToPCM(bs) != null) {
                frames++;
            }
            assertTrue(frames > 0);
            assertNull(decoder.decodeNextFrameToPCM(bs));
        }
    }

    @Test
    @DisplayName("convertToWav() writes a RIFF/WAVE file with PCM payload")
    void convertToWav(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("in.mp3");
        try (InputStream in = openTestMp3()) {
            Files.copy(in, src);
        }
        Path dest = dir.resolve("out.wav");

        Decoder.convertToWav(src, dest);

        byte[] wav = Files.readAllBytes(dest);
        assertTrue(wav.length > 44);
        assertEquals("RIFF", new String(wav, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(wav, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("convertToWav() rejects a missing source")
    void convertToWavMissingSource(@TempDir Path dir) {
        assertThrows(java.io.IOException.class,
                () -> Decoder.convertToWav(dir.resolve("missing.mp3"), dir.resolve("out.wav")));
    }
}
