package me.tamkungz.jlayer.modern;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javazoom.jl.decoder.OutputChannels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import static me.tamkungz.jlayer.modern.TestSupport.TEST_MP3_PATH;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for {@link Mp3Decoder}. */
@DisplayName("Mp3Decoder")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Mp3DecoderTest {
    @Test @Order(1)
    @DisplayName("fromPath() builds a decoder")
    void buildFromPath() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertNotNull(d);
        }
    }

    @Test @Order(2)
    @DisplayName("fromFile() builds a decoder")
    void buildFromFile() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromFile(TEST_MP3_PATH.toString())) {
            assertNotNull(d);
        }
    }

    @Test @Order(3)
    @DisplayName("fromStream() builds a decoder")
    void buildFromStream() throws Exception {
        try (InputStream is = TEST_MP3_PATH.toUri().toURL().openStream();
             Mp3Decoder d = Mp3Decoder.fromStream(is)) {
            assertNotNull(d);
        }
    }

    @Test @Order(4)
    @DisplayName("Builder with OutputChannels.BOTH")
    void builderBoth() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.builder()
                .outputChannels(OutputChannels.BOTH).build(TEST_MP3_PATH)) {
            assertNotNull(d);
        }
    }

    @Test @Order(5)
    @DisplayName("Builder with OutputChannels.LEFT")
    void builderLeft() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.builder()
                .outputChannels(OutputChannels.LEFT).build(TEST_MP3_PATH)) {
            assertNotNull(d);
        }
    }

    @Test @Order(10)
    @DisplayName("getInfo() returns non-null Mp3Info")
    void getInfoNotNull() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertNotNull(d.getInfo());
        }
    }

    @Test @Order(11)
    @DisplayName("Mp3Info has a positive sample rate")
    void getInfoSampleRate() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertTrue(d.getInfo().getSampleRate() > 0);
        }
    }

    @Test @Order(12)
    @DisplayName("Mp3Info reports 1 or 2 channels")
    void getInfoChannels() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            int ch = d.getInfo().getChannels();
            assertTrue(ch == 1 || ch == 2, "Expected 1 or 2, got " + ch);
        }
    }

    @Test @Order(13)
    @DisplayName("getInfo() returns the same cached instance on repeated calls")
    void getInfoCached() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertSame(d.getInfo(), d.getInfo());
        }
    }

    @Test @Order(14)
    @DisplayName("getInfo() then iterate on SAME decoder works")
    void getInfoThenIterateSameDecoderWorks() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertNotNull(d.getInfo());
            int count = 0;
            for (Mp3Frame frame : d) {
                assertNotNull(frame);
                if (++count >= 5) break;
            }
            assertTrue(count > 0, "Iterating after getInfo() on the same decoder must succeed");
        }
    }

    @Test @Order(15)
    @DisplayName("getInfo() on one instance + iterate on a fresh instance — both succeed")
    void getInfoAndIterateSeparateInstances() throws Exception {
        Mp3Info info;
        try (Mp3Decoder infoDecoder = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            info = infoDecoder.getInfo();
        }
        assertNotNull(info);

        int count = 0;
        try (Mp3Decoder frameDecoder = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            for (Mp3Frame f : frameDecoder) { if (++count >= 5) break; }
        }
        assertTrue(count > 0, "Fresh decoder must decode frames");
    }

    @Test @Order(20)
    @DisplayName("iterator() reports hasNext() on a non-empty file")
    void iteratorHasFrames() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertTrue(d.iterator().hasNext());
        }
    }

    @Test @Order(21)
    @DisplayName("forEach() processes every frame")
    void forEachProcessesFrames() throws Exception {
        AtomicInteger n = new AtomicInteger();
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            d.forEach(f -> { assertNotNull(f); n.incrementAndGet(); });
        }
        assertTrue(n.get() > 0, "Expected > 0 frames, got 0");
        System.out.println("forEach: " + n.get() + " frames");
    }

    @Test @Order(22)
    @DisplayName("Mp3Frame exposes non-empty sample data")
    void frameHasValidSamples() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            Mp3Frame f = d.iterator().next();
            short[] s = f.getSamples();
            assertNotNull(s);
            assertTrue(s.length > 0);
            assertTrue(f.getSampleCount() > 0);
            assertEquals(f.getSampleCount(), s.length);
        }
    }

    @Test @Order(23)
    @DisplayName("Frame numbers increase monotonically")
    void frameNumberMonotonic() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            long prev = -1; int n = 0;
            for (Mp3Frame f : d) {
                assertTrue(f.getFrameNumber() > prev);
                prev = f.getFrameNumber();
                if (++n >= 5) break;
            }
        }
    }

    @Test @Order(23)
    @DisplayName("decodeNextFrame() yields frames then eventually EOF null")
    void decodeNextFrameWorks() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            Mp3Frame first = d.decodeNextFrame();
            assertNotNull(first);
            assertTrue(first.getSampleCount() > 0);

            Mp3Frame second = d.decodeNextFrame();
            assertNotNull(second);
            assertTrue(second.getFrameNumber() > first.getFrameNumber());

            Mp3Frame f;
            do {
                f = d.decodeNextFrame();
            } while (f != null);

            assertNull(d.decodeNextFrame());
        }
    }

    @Test @Order(23)
    @DisplayName("decodeUpTo() returns bounded batches")
    void decodeUpToWorks() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            List<Mp3Frame> batch = d.decodeUpTo(3);
            assertEquals(3, batch.size());
            assertTrue(batch.get(0).getFrameNumber() < batch.get(1).getFrameNumber());

            List<Mp3Frame> nextBatch = d.decodeUpTo(2);
            assertEquals(2, nextBatch.size());
            assertTrue(nextBatch.get(0).getFrameNumber() > batch.get(2).getFrameNumber());
        }
    }

    @Test @Order(23)
    @DisplayName("decodeUpTo(0) throws IllegalArgumentException")
    void decodeUpToRejectsNonPositive() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertThrows(IllegalArgumentException.class, () -> d.decodeUpTo(0));
        }
    }

    @Test @Order(23)
    @DisplayName("iterator() then decodeNextFrame() is rejected (single-pass ownership)")
    void iteratorThenDecodeNextFrameRejected() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            Iterator<Mp3Frame> it = d.iterator();
            assertTrue(it.hasNext());
            assertThrows(IllegalStateException.class, d::decodeNextFrame);
        }
    }

    @Test @Order(24)
    @DisplayName("getSamplesCopy() returns a new independent array")
    void samplesCopyIndependent() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            Mp3Frame f = d.iterator().next();
            short[] orig = f.getSamples();
            short[] copy = f.getSamplesCopy();
            assertNotSame(orig, copy);
            assertArrayEquals(orig, copy);
        }
    }

    @Test @Order(30)
    @DisplayName("stream() yields decoded frames")
    void streamYieldsFrames() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH);
             Stream<Mp3Frame> s = d.stream()) {
            assertTrue(s.limit(10).count() > 0);
        }
    }

    @Test @Order(31)
    @DisplayName("stream() supports filter/map")
    void streamSupportsOps() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH);
             Stream<Mp3Frame> s = d.stream()) {
            List<Integer> lengths = s.limit(5).map(Mp3Frame::getSampleCount).toList();
            assertFalse(lengths.isEmpty());
            lengths.forEach(n -> assertTrue(n > 0));
        }
    }

    @Test @Order(40)
    @DisplayName("decodeAll() returns a non-empty list")
    void decodeAllNotEmpty() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            List<Mp3Frame> frames = d.decodeAll();
            assertFalse(frames.isEmpty());
            System.out.println("decodeAll: " + frames.size() + " frames");
        }
    }

    @Test @Order(41)
    @DisplayName("decodeAllPcmLittleEndian() returns non-empty bytes")
    void decodeAllPcmLE() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertTrue(d.decodeAllPcmLittleEndian().length > 0);
        }
    }

    @Test @Order(42)
    @DisplayName("decodeAllPcmBigEndian() returns non-empty bytes")
    void decodeAllPcmBE() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            assertTrue(d.decodeAllPcmBigEndian().length > 0);
        }
    }

    @Test @Order(43)
    @DisplayName("LE and BE PCM arrays have equal length")
    void pcmLeBeSameLength() throws Exception {
        int le, be;
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            le = d.decodeAllPcmLittleEndian().length;
        }
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            be = d.decodeAllPcmBigEndian().length;
        }
        assertEquals(le, be);
    }

    @Test @Order(50)
    @DisplayName("toByteArrayLittleEndian() length == sampleCount * 2")
    void frameToByteLE() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            Mp3Frame f = d.iterator().next();
            assertEquals(f.getSampleCount() * 2, f.toByteArrayLittleEndian().length);
        }
    }

    @Test @Order(51)
    @DisplayName("toByteArrayBigEndian() length == sampleCount * 2")
    void frameToByteBE() throws Exception {
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            Mp3Frame f = d.iterator().next();
            assertEquals(f.getSampleCount() * 2, f.toByteArrayBigEndian().length);
        }
    }

    @Test @Order(60)
    @DisplayName("decodeAsync() completes without error and processes all frames")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void decodeAsync() throws Exception {
        AtomicInteger n = new AtomicInteger();
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            d.decodeAsync(f -> n.incrementAndGet()).join();
        }
        assertTrue(n.get() > 0);
        System.out.println("decodeAsync: " + n.get() + " frames");
    }

    @Test @Order(70)
    @DisplayName("close() is idempotent")
    void closeIdempotent() throws Exception {
        Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH);
        assertDoesNotThrow(d::close);
        assertDoesNotThrow(d::close);
    }

    @Test @Order(71)
    @DisplayName("iterator() after close() throws IllegalStateException")
    void iteratorAfterCloseThrows() throws Exception {
        Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH);
        d.close();
        assertThrows(IllegalStateException.class, d::iterator);
    }

    @Test @Order(72)
    @DisplayName("getInfo() after close() throws IllegalStateException")
    void getInfoAfterCloseThrows() throws Exception {
        Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH);
        d.close();
        assertThrows(IllegalStateException.class, d::getInfo);
    }

    @Test @Order(73)
    @DisplayName("try-with-resources closes decoder cleanly")
    void tryWithResources() {
        // getInfo() on a closed-and-never-played decoder is safe
        assertDoesNotThrow(() -> {
            try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
                d.getInfo();
            }
        });
    }
}
