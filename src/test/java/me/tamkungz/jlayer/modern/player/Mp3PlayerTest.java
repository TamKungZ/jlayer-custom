package me.tamkungz.jlayer.modern.player;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javazoom.jl.player.NullAudioDevice;
import me.tamkungz.jlayer.modern.Mp3Frame;
import me.tamkungz.jlayer.modern.Mp3Info;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import static me.tamkungz.jlayer.modern.TestSupport.TEST_MP3_PATH;
import static me.tamkungz.jlayer.modern.TestSupport.silentDevice;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Tests for {@link Mp3Player}. */
@DisplayName("Mp3Player")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Mp3PlayerTest {
    @Test @Order(1)
    @DisplayName("fromPath() builds a player")
    void buildFromPath() throws Exception {
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build()) {
            assertNotNull(p);
        }
    }

    @Test @Order(2)
    @DisplayName("fromFile() builds a player")
    void buildFromFile() throws Exception {
        try (Mp3Player p = Mp3Player.fromFile(TEST_MP3_PATH.toString())
                .audioDevice(silentDevice()).build()) {
            assertNotNull(p);
        }
    }

    @Test @Order(3)
    @DisplayName("fromStream() builds a player")
    void buildFromStream() throws Exception {
        try (InputStream is = TEST_MP3_PATH.toUri().toURL().openStream();
             Mp3Player p = Mp3Player.fromStream(is)
                     .audioDevice(silentDevice()).build()) {
            assertNotNull(p);
        }
    }

    @Test @Order(10)
    @DisplayName("Initial state is STOPPED and isPlaying() is false")
    void initialState() throws Exception {
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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

    @Test @Order(20)
    @DisplayName("play() transitions state to PLAYING")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void playTransitionsToPlaying() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build();
        p.close();
        assertEquals(Mp3Player.State.CLOSED, p.getState());
    }

    @Test @Order(25)
    @DisplayName("Second play() while PLAYING is a no-op")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void doublePlayNoOp() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build()) {
            assertDoesNotThrow(p::pause);
            assertEquals(Mp3Player.State.STOPPED, p.getState());
        }
    }

    @Test @Order(27)
    @DisplayName("play() on a CLOSED player throws IllegalStateException")
    void playAfterCloseThrows() throws Exception {
        Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build();
        p.close();
        assertThrows(IllegalStateException.class, p::play);
    }

    @Test @Order(30)
    @DisplayName("onStateChanged fires on play()")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void listenerOnStateChanged() throws Exception {
        List<Mp3Player.State> seen = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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

        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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

    @Test @Order(40)
    @DisplayName("playAsync() returns a non-null CompletableFuture")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void playAsync() throws Exception {
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build()) {
            CompletableFuture<Void> f = p.playAsync();
            assertNotNull(f);
            Thread.sleep(500);
            p.stop();
            f.get(5, TimeUnit.SECONDS);
        }
    }

    @Test @Order(50)
    @DisplayName("getPosition() returns 0 before the player is started")
    void getPositionWhenStopped() throws Exception {
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build()) {
            assertEquals(0, p.getPosition());
        }
    }

    @Test @Order(60)
    @DisplayName("close() is idempotent")
    void closeIdempotent() throws Exception {
        Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build();
        assertDoesNotThrow(p::close);
        assertDoesNotThrow(p::close);
    }

    @Test @Order(61)
    @DisplayName("try-with-resources closes player cleanly")
    void tryWithResources() {
        assertDoesNotThrow(() -> {
            try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
