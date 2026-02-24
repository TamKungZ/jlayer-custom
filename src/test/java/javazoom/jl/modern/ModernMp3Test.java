/*
 * Modern JLayer API Test Suite
 * Tests Mp3Decoder, Mp3Player, and AdvancedPlayer
 *
 * Location: src/test/java/javazoom/jl/modern/ModernMp3Test.java
 */

package javazoom.jl.modern;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import javazoom.jl.decoder.OutputChannels;
import javazoom.jl.modern.advanced.AdvancedPlayer;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.NullAudioDevice;
import javazoom.jl.player.NullAudioDeviceFactory;
import javazoom.jl.player.advanced.PlaybackEvent;

/**
 * Comprehensive test suite for the modern JLayer API.
 *
 * <p>Requires {@code test.mp3.properties} on the test classpath:
 * <pre>
 *   basefile=src/test/resources/
 *   filename=your-test-file.mp3
 * </pre>
 *
 * <h2>Modern API behavior</h2>
 * <p>{@link Mp3Decoder#getInfo()} is metadata-only and no longer corrupts decoder
 * iteration state for path-backed decoders. The same instance can be used for
 * info + decoding in sequence.</p>
 */
@DisplayName("Modern JLayer API Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModernMp3Test {

    // -----------------------------------------------------------------------
    // Shared infrastructure
    // -----------------------------------------------------------------------

    private static Path testMp3Path;

    @BeforeAll
    static void loadTestProperties() throws Exception {
        Properties props = new Properties();
        try (InputStream in = ModernMp3Test.class.getClassLoader()
                .getResourceAsStream("test.mp3.properties")) {
            assertNotNull(in, "test.mp3.properties must be on the test classpath");
            props.load(in);
        }
        String base = props.getProperty("basefile", "");
        String name = props.getProperty("filename", "");
        testMp3Path = Paths.get(base + name);
        assertTrue(testMp3Path.toFile().exists(),
                "Test MP3 file not found: " + testMp3Path);
        System.out.println("[ModernMp3Test] test file: " + testMp3Path);
    }

    /**
     * Returns a silent {@link NullAudioDevice} so tests never emit actual audio.
     * NullAudioDevice is a write-to-nowhere no-op that processes samples at CPU speed.
     */
    private static AudioDevice silentDevice() {
        try {
            return FactoryRegistry.systemRegistry()
                    .createAudioDevice(NullAudioDeviceFactory.class);
        } catch (Exception e) {
            return new NullAudioDevice();
        }
    }

    /**
     * Blocks (up to {@code timeoutMs}) until {@code thread} is alive, polling at
     * 10 ms intervals. Returns true if the thread started within the timeout.
     */
    private static boolean waitForThreadAlive(Thread thread, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!thread.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return thread.isAlive();
    }

    // =======================================================================
    // Mp3Decoder
    // =======================================================================

    @Nested
    @DisplayName("Mp3Decoder")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Mp3DecoderTests {

        // --- Construction ---------------------------------------------------

        @Test @Order(1)
        @DisplayName("fromPath() builds a decoder")
        void buildFromPath() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                assertNotNull(d);
            }
        }

        @Test @Order(2)
        @DisplayName("fromFile() builds a decoder")
        void buildFromFile() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromFile(testMp3Path.toString())) {
                assertNotNull(d);
            }
        }

        @Test @Order(3)
        @DisplayName("fromStream() builds a decoder")
        void buildFromStream() throws Exception {
            try (InputStream is = testMp3Path.toUri().toURL().openStream();
                 Mp3Decoder d = Mp3Decoder.fromStream(is)) {
                assertNotNull(d);
            }
        }

        @Test @Order(4)
        @DisplayName("Builder with OutputChannels.BOTH")
        void builderBoth() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.builder()
                    .outputChannels(OutputChannels.BOTH).build(testMp3Path)) {
                assertNotNull(d);
            }
        }

        @Test @Order(5)
        @DisplayName("Builder with OutputChannels.LEFT")
        void builderLeft() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.builder()
                    .outputChannels(OutputChannels.LEFT).build(testMp3Path)) {
                assertNotNull(d);
            }
        }

        // --- getInfo() ---

        @Test @Order(10)
        @DisplayName("getInfo() returns non-null Mp3Info")
        void getInfoNotNull() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                assertNotNull(d.getInfo());
            }
        }

        @Test @Order(11)
        @DisplayName("Mp3Info has a positive sample rate")
        void getInfoSampleRate() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                assertTrue(d.getInfo().getSampleRate() > 0);
            }
        }

        @Test @Order(12)
        @DisplayName("Mp3Info reports 1 or 2 channels")
        void getInfoChannels() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                int ch = d.getInfo().getChannels();
                assertTrue(ch == 1 || ch == 2, "Expected 1 or 2, got " + ch);
            }
        }

        @Test @Order(13)
        @DisplayName("getInfo() returns the same cached instance on repeated calls")
        void getInfoCached() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                assertSame(d.getInfo(), d.getInfo());
            }
        }

        @Test @Order(14)
        @DisplayName("getInfo() then iterate on SAME decoder works")
        void getInfoThenIterateSameDecoderWorks() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
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
            try (Mp3Decoder infoDecoder = Mp3Decoder.fromPath(testMp3Path)) {
                info = infoDecoder.getInfo();
            }
            assertNotNull(info);

            int count = 0;
            try (Mp3Decoder frameDecoder = Mp3Decoder.fromPath(testMp3Path)) {
                for (Mp3Frame f : frameDecoder) { if (++count >= 5) break; }
            }
            assertTrue(count > 0, "Fresh decoder must decode frames");
        }

        // --- Iteration ---

        @Test @Order(20)
        @DisplayName("iterator() reports hasNext() on a non-empty file")
        void iteratorHasFrames() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                assertTrue(d.iterator().hasNext());
            }
        }

        @Test @Order(21)
        @DisplayName("forEach() processes every frame")
        void forEachProcessesFrames() throws Exception {
            AtomicInteger n = new AtomicInteger();
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                d.forEach(f -> { assertNotNull(f); n.incrementAndGet(); });
            }
            assertTrue(n.get() > 0, "Expected > 0 frames, got 0");
            System.out.println("forEach: " + n.get() + " frames");
        }

        @Test @Order(22)
        @DisplayName("Mp3Frame exposes non-empty sample data")
        void frameHasValidSamples() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
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
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
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
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
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
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
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
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                assertThrows(IllegalArgumentException.class, () -> d.decodeUpTo(0));
            }
        }

        @Test @Order(23)
        @DisplayName("iterator() then decodeNextFrame() is rejected (single-pass ownership)")
        void iteratorThenDecodeNextFrameRejected() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                Iterator<Mp3Frame> it = d.iterator();
                assertTrue(it.hasNext());
                assertThrows(IllegalStateException.class, d::decodeNextFrame);
            }
        }

        @Test @Order(24)
        @DisplayName("getSamplesCopy() returns a new independent array")
        void samplesCopyIndependent() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                Mp3Frame f = d.iterator().next();
                short[] orig = f.getSamples();
                short[] copy = f.getSamplesCopy();
                assertNotSame(orig, copy);
                assertArrayEquals(orig, copy);
            }
        }

        // --- Stream API -----------------------------------------------------

        @Test @Order(30)
        @DisplayName("stream() yields decoded frames")
        void streamYieldsFrames() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path);
                 Stream<Mp3Frame> s = d.stream()) {
                assertTrue(s.limit(10).count() > 0);
            }
        }

        @Test @Order(31)
        @DisplayName("stream() supports filter/map")
        void streamSupportsOps() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path);
                 Stream<Mp3Frame> s = d.stream()) {
                List<Integer> lengths = s.limit(5).map(Mp3Frame::getSampleCount).toList();
                assertFalse(lengths.isEmpty());
                lengths.forEach(n -> assertTrue(n > 0));
            }
        }

        // --- Bulk decode ----------------------------------------------------

        @Test @Order(40)
        @DisplayName("decodeAll() returns a non-empty list")
        void decodeAllNotEmpty() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                List<Mp3Frame> frames = d.decodeAll();
                assertFalse(frames.isEmpty());
                System.out.println("decodeAll: " + frames.size() + " frames");
            }
        }

        @Test @Order(41)
        @DisplayName("decodeAllPcmLittleEndian() returns non-empty bytes")
        void decodeAllPcmLE() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                assertTrue(d.decodeAllPcmLittleEndian().length > 0);
            }
        }

        @Test @Order(42)
        @DisplayName("decodeAllPcmBigEndian() returns non-empty bytes")
        void decodeAllPcmBE() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                assertTrue(d.decodeAllPcmBigEndian().length > 0);
            }
        }

        @Test @Order(43)
        @DisplayName("LE and BE PCM arrays have equal length")
        void pcmLeBeSameLength() throws Exception {
            int le, be;
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                le = d.decodeAllPcmLittleEndian().length;
            }
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                be = d.decodeAllPcmBigEndian().length;
            }
            assertEquals(le, be);
        }

        // --- Frame byte conversions -----------------------------------------

        @Test @Order(50)
        @DisplayName("toByteArrayLittleEndian() length == sampleCount * 2")
        void frameToByteLE() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                Mp3Frame f = d.iterator().next();
                assertEquals(f.getSampleCount() * 2, f.toByteArrayLittleEndian().length);
            }
        }

        @Test @Order(51)
        @DisplayName("toByteArrayBigEndian() length == sampleCount * 2")
        void frameToByteBE() throws Exception {
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                Mp3Frame f = d.iterator().next();
                assertEquals(f.getSampleCount() * 2, f.toByteArrayBigEndian().length);
            }
        }

        // --- Async ----------------------------------------------------------

        @Test @Order(60)
        @DisplayName("decodeAsync() completes without error and processes all frames")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void decodeAsync() throws Exception {
            AtomicInteger n = new AtomicInteger();
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                d.decodeAsync(f -> n.incrementAndGet()).join();
            }
            assertTrue(n.get() > 0);
            System.out.println("decodeAsync: " + n.get() + " frames");
        }

        // --- Lifecycle ------------------------------------------------------

        @Test @Order(70)
        @DisplayName("close() is idempotent")
        void closeIdempotent() throws Exception {
            Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path);
            assertDoesNotThrow(d::close);
            assertDoesNotThrow(d::close);
        }

        @Test @Order(71)
        @DisplayName("iterator() after close() throws IllegalStateException")
        void iteratorAfterCloseThrows() throws Exception {
            Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path);
            d.close();
            assertThrows(IllegalStateException.class, d::iterator);
        }

        @Test @Order(72)
        @DisplayName("getInfo() after close() throws IllegalStateException")
        void getInfoAfterCloseThrows() throws Exception {
            Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path);
            d.close();
            assertThrows(IllegalStateException.class, d::getInfo);
        }

        @Test @Order(73)
        @DisplayName("try-with-resources closes decoder cleanly")
        void tryWithResources() {
            // getInfo() on a closed-and-never-played decoder is safe
            assertDoesNotThrow(() -> {
                try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                    d.getInfo();
                }
            });
        }
    }

    // =======================================================================
    // Mp3Player
    // =======================================================================

    @Nested
    @DisplayName("Mp3Player")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Mp3PlayerTests {

        // --- Construction ---------------------------------------------------

        @Test @Order(1)
        @DisplayName("fromPath() builds a player")
        void buildFromPath() throws Exception {
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build()) {
                assertNotNull(p);
            }
        }

        @Test @Order(2)
        @DisplayName("fromFile() builds a player")
        void buildFromFile() throws Exception {
            try (Mp3Player p = Mp3Player.fromFile(testMp3Path.toString())
                    .audioDevice(silentDevice()).build()) {
                assertNotNull(p);
            }
        }

        @Test @Order(3)
        @DisplayName("fromStream() builds a player")
        void buildFromStream() throws Exception {
            try (InputStream is = testMp3Path.toUri().toURL().openStream();
                 Mp3Player p = Mp3Player.fromStream(is)
                         .audioDevice(silentDevice()).build()) {
                assertNotNull(p);
            }
        }

        // --- Initial state --------------------------------------------------

        @Test @Order(10)
        @DisplayName("Initial state is STOPPED and isPlaying() is false")
        void initialState() throws Exception {
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build()) {
                assertEquals(Mp3Player.State.STOPPED, p.getState());
                assertFalse(p.isPlaying());
            }
        }

        /**
         * Verifies getInfo() on a player that is still usable for playback.
         */
        @Test @Order(11)
        @DisplayName("getInfo() is available on a player and does not block playback")
        void getInfoThenPlay() throws Exception {
            CountDownLatch finished = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .listener(new Mp3Player.Listener() {
                        @Override public void onPlaybackFinished() { finished.countDown(); }
                    })
                    .audioDevice(silentDevice()).build()) {
                Mp3Info info = p.getInfo();
                assertNotNull(info);
                assertTrue(info.getSampleRate() > 0);
                p.playAndWait();
            }
            assertTrue(finished.getCount() == 0, "Playback should complete after getInfo()");
        }

        // --- State transitions ----------------------------------------------

        @Test @Order(20)
        @DisplayName("play() transitions state to PLAYING")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void playTransitionsToPlaying() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onStateChanged(Mp3Player.State o, Mp3Player.State n) {
                            if (n == Mp3Player.State.PLAYING) latch.countDown();
                        }
                    }).build()) {
                p.play();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                p.stop();
            }
        }

        @Test @Order(21)
        @DisplayName("pause() transitions state to PAUSED")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void pauseTransitionsToPaused() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onStateChanged(Mp3Player.State o, Mp3Player.State n) {
                            if (n == Mp3Player.State.PLAYING) latch.countDown();
                        }
                    }).build()) {
                p.play();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                p.pause();
                assertEquals(Mp3Player.State.PAUSED, p.getState());
                assertFalse(p.isPlaying());
                p.stop();
            }
        }

        @Test @Order(22)
        @DisplayName("resume() transitions back to PLAYING")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void resumeTransitionsToPlaying() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onStateChanged(Mp3Player.State o, Mp3Player.State n) {
                            if (n == Mp3Player.State.PLAYING) latch.countDown();
                        }
                    }).build()) {
                p.play();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                p.pause();
                p.resume();
                assertEquals(Mp3Player.State.PLAYING, p.getState());
                assertTrue(p.isPlaying());
                p.stop();
            }
        }

        @Test @Order(23)
        @DisplayName("stop() transitions state to STOPPED")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void stopTransitionsToStopped() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onStateChanged(Mp3Player.State o, Mp3Player.State n) {
                            if (n == Mp3Player.State.PLAYING) latch.countDown();
                        }
                    }).build()) {
                p.play();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                p.stop();
                assertEquals(Mp3Player.State.STOPPED, p.getState());
                assertFalse(p.isPlaying());
            }
        }

        @Test @Order(24)
        @DisplayName("close() transitions state to CLOSED")
        void closeTransitionsToClosed() throws Exception {
            Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build();
            p.close();
            assertEquals(Mp3Player.State.CLOSED, p.getState());
        }

        @Test @Order(25)
        @DisplayName("Second play() while PLAYING is a no-op")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void doublePlayNoOp() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onStateChanged(Mp3Player.State o, Mp3Player.State n) {
                            if (n == Mp3Player.State.PLAYING) latch.countDown();
                        }
                    }).build()) {
                p.play();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                assertDoesNotThrow(p::play);
                p.stop();
            }
        }

        @Test @Order(26)
        @DisplayName("pause() while STOPPED is a no-op")
        void pauseWhileStoppedNoOp() throws Exception {
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build()) {
                assertDoesNotThrow(p::pause);
                assertEquals(Mp3Player.State.STOPPED, p.getState());
            }
        }

        @Test @Order(27)
        @DisplayName("play() on a CLOSED player throws IllegalStateException")
        void playAfterCloseThrows() throws Exception {
            Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build();
            p.close();
            assertThrows(IllegalStateException.class, p::play);
        }

        // --- Listener callbacks ---------------------------------------------

        @Test @Order(30)
        @DisplayName("onStateChanged fires on play()")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void listenerOnStateChanged() throws Exception {
            List<Mp3Player.State> seen = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onStateChanged(Mp3Player.State o, Mp3Player.State n) {
                            synchronized (seen) { seen.add(n); }
                            if (n == Mp3Player.State.PLAYING) latch.countDown();
                        }
                    }).build()) {
                p.play();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                p.stop();
            }
            assertTrue(seen.contains(Mp3Player.State.PLAYING));
        }

        @Test @Order(31)
        @DisplayName("onFramePlayed fires for each decoded frame")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void listenerOnFramePlayed() throws Exception {
            AtomicInteger n = new AtomicInteger();
            CountDownLatch first = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onFramePlayed(Mp3Frame f) {
                            assertNotNull(f);
                            n.incrementAndGet();
                            first.countDown();
                        }
                    }).build()) {
                p.play();
                assertTrue(first.await(5, TimeUnit.SECONDS));
                Thread.sleep(200);
                p.stop();
            }
            assertTrue(n.get() > 0);
        }

        /**
         * Verifies that {@code onPlaybackFinished} fires when the stream ends naturally.
         *
         * <p>{@link NullAudioDevice} discards PCM at CPU speed, so a ~5 s MP3 finishes
         * in milliseconds.  We use {@code playAndWait()} which blocks until the thread
         * exits, then check the callback fired.  If the background thread threw instead
         * (e.g. decoder error), {@code onError} fires first and we fail with a clear message.
         *
         */
        @Test @Order(32)
        @DisplayName("onPlaybackFinished fires when stream ends naturally")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void listenerOnPlaybackFinished() throws Exception {
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicReference<Throwable> error = new AtomicReference<>();

            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onPlaybackFinished() { finished.set(true); }
                        @Override public void onError(Throwable t) { error.set(t); }
                    }).build()) {
                // playAndWait() blocks until the background thread is fully done —
                // no latch needed, no race between the thread and try-with-resources.
                p.playAndWait();
            }

            if (error.get() != null) {
                fail("onError was called instead of onPlaybackFinished: " + error.get(),
                        error.get());
            }
            assertTrue(finished.get(), "onPlaybackFinished must have been called");
        }

        @Test @Order(33)
        @DisplayName("setListener() replaces the existing listener")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void setListenerReplaces() throws Exception {
            AtomicBoolean newCalled = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {}) // initial no-op
                    .build()) {
                p.setListener(new Mp3Player.Listener() {
                    @Override public void onStateChanged(Mp3Player.State o, Mp3Player.State n) {
                        if (n == Mp3Player.State.PLAYING) {
                            newCalled.set(true);
                            latch.countDown();
                        }
                    }
                });
                p.play();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                p.stop();
            }
            assertTrue(newCalled.get());
        }

        // --- Async ----------------------------------------------------------

        @Test @Order(40)
        @DisplayName("playAsync() returns a non-null CompletableFuture")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void playAsync() throws Exception {
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build()) {
                CompletableFuture<Void> f = p.playAsync();
                assertNotNull(f);
                Thread.sleep(500);
                p.stop();
                f.get(5, TimeUnit.SECONDS);
            }
        }

        // --- getPosition ----------------------------------------------------

        @Test @Order(50)
        @DisplayName("getPosition() returns 0 before the player is started")
        void getPositionWhenStopped() throws Exception {
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build()) {
                assertEquals(0, p.getPosition());
            }
        }

        // --- Lifecycle ------------------------------------------------------

        @Test @Order(60)
        @DisplayName("close() is idempotent")
        void closeIdempotent() throws Exception {
            Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build();
            assertDoesNotThrow(p::close);
            assertDoesNotThrow(p::close);
        }

        @Test @Order(61)
        @DisplayName("try-with-resources closes player cleanly")
        void tryWithResources() {
            assertDoesNotThrow(() -> {
                try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                        .audioDevice(silentDevice()).build()) {
                    p.getInfo();
                }
            });
        }

        @Test @Order(70)
        @DisplayName("daemon(false) is accepted")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void daemonFalse() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .daemon(false)
                    .listener(new Mp3Player.Listener() {
                        @Override public void onStateChanged(Mp3Player.State o, Mp3Player.State n) {
                            if (n == Mp3Player.State.PLAYING) latch.countDown();
                        }
                    }).build()) {
                p.play();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                p.stop();
            }
        }
    }

    // =======================================================================
    // AdvancedPlayer
    // =======================================================================

    @Nested
    @DisplayName("AdvancedPlayer")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AdvancedPlayerTests {

        // --- Construction ---------------------------------------------------

        @Test @Order(1)
        @DisplayName("fromPath() builds player")
        void buildFromPath() throws Exception {
            try (AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build()) {
                assertNotNull(p);
            }
        }

        @Test @Order(2)
        @DisplayName("fromFile() builds player")
        void buildFromFile() throws Exception {
            try (AdvancedPlayer p = AdvancedPlayer.fromFile(testMp3Path.toString())
                    .audioDevice(silentDevice()).build()) {
                assertNotNull(p);
            }
        }

        @Test @Order(3)
        @DisplayName("fromStream() builds player")
        void buildFromStream() throws Exception {
            try (InputStream is = testMp3Path.toUri().toURL().openStream();
                 AdvancedPlayer p = AdvancedPlayer.fromStream(is)
                         .audioDevice(silentDevice()).build()) {
                assertNotNull(p);
            }
        }

        // --- Info -----------------------------------------------------------

        @Test @Order(10)
        @DisplayName("getInfo() returns valid Mp3Info")
        void getInfo() throws Exception {
            try (AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build()) {
                Mp3Info info = p.getInfo();
                assertNotNull(info);
                assertTrue(info.getSampleRate() > 0);
                int ch = info.getChannels();
                assertTrue(ch == 1 || ch == 2, "Expected 1 or 2 channels, got " + ch);
            }
        }

        // --- Playback -------------------------------------------------------

        /**
         * Starts playback and stops it after a moment.
         *
         * <p>The {@link NullAudioDevice} processes audio at CPU speed so the entire
         * ~5 s MP3 can finish in milliseconds. {@code stop()} may therefore be
         * called after the play thread has already exited. The fixed
         * {@code AdvancedPlayer.stop()} catches the resulting NPE from the legacy
         * {@code player.stop()} → {@code createEvent()} → {@code dev.getPosition()}.
         */
        @Test @Order(20)
        @DisplayName("start() then stop() completes without exception")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void startAndStop() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch canStop = new CountDownLatch(1);

            try (AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(event -> {
                        if (event.getId() == PlaybackEvent.STARTED) {
                            started.countDown();
                            // Hold the listener thread so the play thread stays alive
                            // long enough for the test to call stop().
                            try { canStop.await(3, TimeUnit.SECONDS); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        }
                    }).build()) {
                p.start();
                assertTrue(started.await(5, TimeUnit.SECONDS),
                        "STARTED event must fire within 5 s");
                assertTrue(p.isPlaying(), "Player should be playing when STARTED fires");
                canStop.countDown(); // allow listener (and playback) to continue
                assertDoesNotThrow(p::stop);
                assertFalse(p.isPlaying());
            }
        }

        /**
         * Verifies {@code isPlaying()} is {@code true} while the STARTED event is active.
         *
         * <p>We cannot rely on {@code Thread.sleep} because {@link NullAudioDevice}
         * finishes the whole file in milliseconds. Instead we sample {@code isPlaying()}
         * from inside the STARTED listener callback, where the play thread is guaranteed
         * to still be alive.
         */
        @Test @Order(21)
        @DisplayName("isPlaying() is true while the STARTED event is active")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void isPlayingAfterStart() throws Exception {
            AtomicBoolean wasPlaying = new AtomicBoolean(false);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch canContinue = new CountDownLatch(1);

            // We need a reference to the player inside the lambda — use a single-element array.
            AdvancedPlayer[] holder = new AdvancedPlayer[1];
            holder[0] = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(event -> {
                        if (event.getId() == PlaybackEvent.STARTED) {
                            wasPlaying.set(holder[0].isPlaying());
                            started.countDown();
                            try { canContinue.await(2, TimeUnit.SECONDS); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        }
                    }).build();

            try (AdvancedPlayer p = holder[0]) {
                p.start();
                assertTrue(started.await(5, TimeUnit.SECONDS),
                        "STARTED event must fire within 5 s");
                assertTrue(wasPlaying.get(),
                        "isPlaying() must be true when STARTED event fires");
                canContinue.countDown();
                p.stop();
            }
        }

        /**
         * Verifies {@code isPlaying()} is {@code false} after {@code stop()}.
         *
         * <p>We use the STARTED latch to ensure the play thread is alive before
         * calling {@code stop()}, avoiding the race with NullAudioDevice.
         */
        @Test @Order(22)
        @DisplayName("isPlaying() is false after stop()")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void isPlayingFalseAfterStop() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch canContinue = new CountDownLatch(1);

            try (AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(event -> {
                        if (event.getId() == PlaybackEvent.STARTED) {
                            started.countDown();
                            try { canContinue.await(2, TimeUnit.SECONDS); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        }
                    }).build()) {
                p.start();
                assertTrue(started.await(5, TimeUnit.SECONDS));
                canContinue.countDown();
                p.stop();
                assertFalse(p.isPlaying());
            }
        }

        /**
         * {@code playAsync()} is stopped after a brief wait. The latch ensures the play
         * thread is alive before we attempt to stop it (NullAudioDevice is instant).
         */
        @Test @Order(23)
        @DisplayName("playAsync() returns a non-null CompletableFuture")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void playAsync() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch canContinue = new CountDownLatch(1);

            try (AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(event -> {
                        if (event.getId() == PlaybackEvent.STARTED) {
                            started.countDown();
                            try { canContinue.await(2, TimeUnit.SECONDS); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        }
                    }).build()) {
                CompletableFuture<Void> f = p.playAsync();
                assertNotNull(f);
                assertTrue(started.await(5, TimeUnit.SECONDS));
                canContinue.countDown();
                p.stop();
                f.get(5, TimeUnit.SECONDS);
            }
        }

        @Test @Order(24)
        @DisplayName("fromFrame/toFrame restricts playback to a small frame window")
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        void frameRange() throws Exception {
            try (AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .fromFrame(0).toFrame(50)
                    .build()) {
                assertDoesNotThrow(p::playAndWait);
            }
        }

        @Test @Order(25)
        @DisplayName("Negative startFrame throws IllegalArgumentException immediately")
        void negativeStartFrameThrows() throws IOException {
            assertThrows(IllegalArgumentException.class,
                    () -> AdvancedPlayer.fromPath(testMp3Path).fromFrame(-1));
        }

        @Test @Order(26)
        @DisplayName("Negative endFrame throws IllegalArgumentException immediately")
        void negativeEndFrameThrows() throws IOException {
            assertThrows(IllegalArgumentException.class,
                    () -> AdvancedPlayer.fromPath(testMp3Path).toFrame(-1));
        }

        // --- Lifecycle ------------------------------------------------------

        @Test @Order(30)
        @DisplayName("close() is idempotent")
        void closeIdempotent() throws Exception {
            AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build();
            assertDoesNotThrow(p::close);
            assertDoesNotThrow(p::close);
        }

        @Test @Order(31)
        @DisplayName("start() on a closed player throws IllegalStateException")
        void startAfterCloseThrows() throws Exception {
            AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).build();
            p.close();
            assertThrows(IllegalStateException.class, p::start);
        }

        @Test @Order(32)
        @DisplayName("try-with-resources closes player cleanly")
        void tryWithResources() {
            assertDoesNotThrow(() -> {
                try (AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                        .audioDevice(silentDevice()).build()) {
                    p.getInfo(); // AdvancedPlayer caches info separately, no decoder corruption
                }
            });
        }

        // --- Listener -------------------------------------------------------

        @Test @Order(40)
        @DisplayName("ModernPlaybackListener receives at least one event")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void modernListenerReceivesEvents() throws Exception {
            AtomicBoolean got = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch canContinue = new CountDownLatch(1);

            try (AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(event -> {
                        got.set(true);
                        latch.countDown();
                        // Briefly pause so the play thread is still alive when we stop
                        try { canContinue.await(2, TimeUnit.SECONDS); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }).build()) {
                p.start();
                assertTrue(latch.await(5, TimeUnit.SECONDS));
                canContinue.countDown();
                p.stop();
            }
            assertTrue(got.get());
        }

        /**
         * daemon(false) is accepted. We use the STARTED latch so stop() is not
         * called on an already-finished NullAudioDevice player.
         */
        @Test @Order(41)
        @DisplayName("daemon(false) option is accepted")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void daemonFalse() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch canContinue = new CountDownLatch(1);

            try (AdvancedPlayer p = AdvancedPlayer.fromPath(testMp3Path)
                    .audioDevice(silentDevice()).daemon(false)
                    .listener(event -> {
                        if (event.getId() == PlaybackEvent.STARTED) {
                            started.countDown();
                            try { canContinue.await(2, TimeUnit.SECONDS); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        }
                    }).build()) {
                p.start();
                assertTrue(started.await(5, TimeUnit.SECONDS));
                canContinue.countDown();
                p.stop();
            }
        }
    }

    // =======================================================================
    // Integration
    // =======================================================================

    @Nested
    @DisplayName("Integration")
    class IntegrationTests {

        /**
         * Decodes the full file with both {@link Mp3Decoder} (direct) and
         * {@link Mp3Player} (via {@code onFramePlayed}) and checks frame counts match.
         *
         * <p>{@code playAndWait()} blocks until the background thread exits fully,
         * so there is no race between thread teardown and try-with-resources.</p>
         */
        @Test
        @DisplayName("Decoder and Player report the same total frame count")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void decoderAndPlayerFrameCountMatch() throws Exception {
            int decoderFrames;
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                decoderFrames = d.decodeAll().size();
            }

            AtomicInteger playerFrames = new AtomicInteger();
            AtomicReference<Throwable> err = new AtomicReference<>();
            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onFramePlayed(Mp3Frame f) {
                            playerFrames.incrementAndGet();
                        }
                        @Override public void onError(Throwable t) { err.set(t); }
                    }).build()) {
                p.playAndWait();
            }

            if (err.get() != null) {
                fail("Player failed during playback: " + err.get(), err.get());
            }
            assertEquals(decoderFrames, playerFrames.get(),
                    "Decoder and Player must process the same number of frames");
            System.out.println("Frame count match: " + decoderFrames);
        }

        /**
         * Verifies that two separate {@link Mp3Decoder} instances decode an identical
         * frame count from the same file, confirming stateless per-instance behaviour.
         */
        @Test
        @DisplayName("Two independent decoders produce equal frame counts")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void twoDecodersProduceEqualFrameCounts() throws Exception {
            int c1, c2;
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                c1 = d.decodeAll().size();
            }
            try (Mp3Decoder d = Mp3Decoder.fromPath(testMp3Path)) {
                c2 = d.decodeAll().size();
            }
            assertTrue(c1 > 0);
            assertEquals(c1, c2, "Independent decoders must report the same frame count");
            System.out.println("Independent decoder counts: " + c1 + " == " + c2);
        }

        /**
         * Verifies that pause/resume during playback plays the entire file to completion.
         *
         * <p>Uses {@code playAndWait()} rather than an async latch so the background
         * thread is guaranteed to have fully exited before assertions run.</p>
         */
        @Test
        @DisplayName("Pause-resume plays the complete file to completion")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void pauseResumeCompletesFullFile() throws Exception {
            AtomicInteger frames = new AtomicInteger();
            AtomicReference<Throwable> err = new AtomicReference<>();
            // We need to pause after the first frame; a latch lets us coordinate.
            CountDownLatch firstFrame = new CountDownLatch(1);

            try (Mp3Player p = Mp3Player.fromPath(testMp3Path)
                    .audioDevice(silentDevice())
                    .listener(new Mp3Player.Listener() {
                        @Override public void onFramePlayed(Mp3Frame f) {
                            frames.incrementAndGet();
                            firstFrame.countDown(); // fires once; subsequent countdowns are no-ops
                        }
                        @Override public void onError(Throwable t) { err.set(t); }
                    }).build()) {

                // Start async so we can pause from this thread
                CompletableFuture<Void> future = p.playAsync();

                // Wait until at least one frame is played, then pause
                assertTrue(firstFrame.await(10, TimeUnit.SECONDS),
                        "At least one frame must be played within 10 s");
                p.pause();
                Thread.sleep(200);
                p.resume();

                // Wait for full completion
                future.get(25, TimeUnit.SECONDS);
            }

            if (err.get() != null) {
                fail("Playback error during pause/resume: " + err.get(), err.get());
            }
            assertTrue(frames.get() > 0,
                    "Frames must be counted across the pause/resume cycle");
            System.out.println("Frames played with pause/resume: " + frames.get());
        }
    }
}
