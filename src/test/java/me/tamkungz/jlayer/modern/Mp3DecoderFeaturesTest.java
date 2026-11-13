package me.tamkungz.jlayer.modern;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static me.tamkungz.jlayer.modern.TestSupport.TEST_MP3_PATH;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Mp3Decoder additions")
class Mp3DecoderFeaturesTest {

    @Test
    @DisplayName("every frame carries its own decoded samples")
    void framesAreIndependent() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            List<Mp3Frame> frames = d.decodeUpTo(20);
            assertEquals(20, frames.size());
            for (Mp3Frame f : frames) {
                assertEquals(f.getSampleFrameCount() * f.getChannels(), f.getSampleCount());
                assertTrue(f.getSampleCount() > 0, "frame " + f.getFrameNumber() + " has no samples");
            }
            assertTrue(frames.stream().map(Mp3Frame::getSamplesCopy).distinct().count() > 1,
                    "frames must not share one sample buffer");
        }
    }

    @Test
    @DisplayName("decoded PCM is not silent")
    void pcmIsNotSilent() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            byte[] pcm = d.decodeAllPcmLittleEndian();
            boolean nonZero = false;
            for (byte b : pcm) {
                if (b != 0) {
                    nonZero = true;
                    break;
                }
            }
            assertTrue(nonZero);
        }
    }

    @Test
    @DisplayName("frame duration follows sample rate")
    void frameDuration() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            Mp3Frame f = d.decodeNextFrame();
            assertEquals(f.getSampleFrameCount() * 1000.0 / f.getSampleRate(), f.getDurationMs(), 1e-9);
        }
    }

    @Test
    @DisplayName("skipFrames() advances the stream without decoding")
    void skipFrames() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertEquals(10, d.skipFrames(10));
            assertEquals(10, d.decodeNextFrame().getFrameNumber());
            assertEquals(0, d.skipFrames(0));
            assertEquals(11, d.decodeNextFrame().getFrameNumber());
        }
    }

    @Test
    @DisplayName("skipFrames() stops at end of stream")
    void skipFramesPastEnd() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            int total = d.skipFrames(Integer.MAX_VALUE);
            assertTrue(total > 0);
            assertEquals(0, d.skipFrames(5));
            assertNull(d.decodeNextFrame());
        }
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertEquals(d.decodeAll().size(), total(TEST_MP3_PATH));
        }
    }

    private static int total(Path path) throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(path)) {
            return d.skipFrames(Integer.MAX_VALUE);
        }
    }

    @Test
    @DisplayName("skipFrames() validates its argument and shares the single pass")
    void skipFramesRules() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertThrows(IllegalArgumentException.class, () -> d.skipFrames(-1));
            d.iterator();
            assertThrows(IllegalStateException.class, () -> d.skipFrames(1));
        }
    }

    @Test
    @DisplayName("writeWav(Path) writes a valid header and the full PCM payload")
    void writeWavFile(@TempDir Path dir) throws Exception {
        byte[] pcm;
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            pcm = d.decodeAllPcmLittleEndian();
        }
        Path wav = dir.resolve("out.wav");
        Mp3Info info;
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            info = d.getInfo();
            d.writeWav(wav);
        }

        byte[] bytes = Files.readAllBytes(wav);
        assertEquals(44 + pcm.length, bytes.length);
        assertWavHeader(bytes, info.getSampleRate(), info.getChannels(), pcm.length);
        assertArrayEquals(pcm, java.util.Arrays.copyOfRange(bytes, 44, bytes.length));
    }

    @Test
    @DisplayName("writeWav(OutputStream) writes the same bytes as writeWav(Path)")
    void writeWavStream(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("a.wav");
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            d.writeWav(wav);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            d.writeWav(out);
        }
        assertArrayEquals(Files.readAllBytes(wav), out.toByteArray());
    }

    @Test
    @DisplayName("writeWav(Path) keeps an existing file when the decoder was already consumed")
    void writeWavDoesNotClobber(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("keep.wav");
        Files.writeString(wav, "keep");
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            d.decodeAll();
            assertThrows(IllegalStateException.class, () -> d.writeWav(wav));
        }
        assertEquals("keep", Files.readString(wav));
    }

    @Test
    @DisplayName("writeWav() rejects a stream without frames")
    void writeWavEmpty(@TempDir Path dir) {
        try (Mp3Decoder d = Mp3Decoder.fromStream(new ByteArrayInputStream(new byte[0]))) {
            assertThrows(Mp3Exception.class, () -> d.writeWav(new ByteArrayOutputStream()));
        }
    }

    @Test
    @DisplayName("Mp3Info.read() matches the decoder info and exposes a duration")
    void infoRead() throws Exception {
        Mp3Info info = Mp3Info.read(TEST_MP3_PATH);
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertEquals(d.getInfo(), info);
        }
        assertTrue(info.getDuration().orElseThrow().toMillis() > 0);
    }

    @Test
    @DisplayName("Mp3Info.probe() without a known size has no duration and leaves the stream open")
    void infoProbeUnknownSize() throws Exception {
        try (var in = Files.newInputStream(TEST_MP3_PATH)) {
            Mp3Info info = Mp3Info.probe(in, -1);
            assertTrue(info.getDuration().isEmpty());
            in.read();
        }
    }

    @Test
    @DisplayName("a large ID3v2 tag does not break decoding or probing")
    void largeId3Tag(@TempDir Path dir) throws Exception {
        byte[] mp3 = Files.readAllBytes(TEST_MP3_PATH);
        int padding = 300_000;
        ByteBuffer tag = ByteBuffer.allocate(10 + padding).order(ByteOrder.BIG_ENDIAN);
        tag.put("ID3".getBytes(StandardCharsets.ISO_8859_1)).put((byte) 3).put((byte) 0).put((byte) 0);
        tag.put((byte) ((padding >> 21) & 0x7F)).put((byte) ((padding >> 14) & 0x7F))
                .put((byte) ((padding >> 7) & 0x7F)).put((byte) (padding & 0x7F));
        byte[] data = new byte[tag.capacity() + mp3.length];
        System.arraycopy(tag.array(), 0, data, 0, tag.capacity());
        System.arraycopy(mp3, 0, data, tag.capacity(), mp3.length);
        Path file = dir.resolve("big-tag.mp3");
        Files.write(file, data);

        Mp3Info info = Mp3Info.read(file);
        assertNotNull(info);
        try (Mp3Decoder d = Mp3Decoder.fromPath(file)) {
            assertFalse(d.decodeUpTo(3).isEmpty());
        }
    }

    @Test
    @DisplayName("getTags() works for path-backed decoders and is empty for streams")
    void getTags() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertNotNull(d.getTags());
            assertEquals(d.getTags(), d.getTags());
        }
        try (Mp3Decoder d = Mp3Decoder.fromStream(Files.newInputStream(TEST_MP3_PATH))) {
            assertTrue(d.getTags().isEmpty());
        }
    }

    @Test
    @DisplayName("outputBuffer() only accepts a SampleBuffer")
    void outputBufferType() {
        assertThrows(IllegalArgumentException.class, () -> Mp3Decoder.builder().outputBuffer(new javazoom.jl.decoder.Obuffer() {
            @Override public void append(int channel, short value) { }
            @Override public void writeBuffer(int val) { }
            @Override public void close() { }
            @Override public void clearBuffer() { }
            @Override public void setStopFlag() { }
        }));
    }

    private static void assertWavHeader(byte[] wav, int sampleRate, int channels, int dataLength) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(wav.length - 8, b.getInt(4));
        assertEquals("WAVEfmt ", new String(wav, 8, 8, StandardCharsets.US_ASCII));
        assertEquals(16, b.getInt(16));
        assertEquals(1, b.getShort(20));
        assertEquals(channels, b.getShort(22));
        assertEquals(sampleRate, b.getInt(24));
        assertEquals(sampleRate * channels * 2, b.getInt(28));
        assertEquals(channels * 2, b.getShort(32));
        assertEquals(16, b.getShort(34));
        assertEquals("data", new String(wav, 36, 4, StandardCharsets.US_ASCII));
        assertEquals(dataLength, b.getInt(40));
    }
}
