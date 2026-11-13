package me.tamkungz.jlayer.modern.player;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javazoom.jl.player.NullAudioDevice;
import javazoom.jl.player.advanced.PlaybackEvent;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for {@link AdvancedPlayer}. */
@DisplayName("AdvancedPlayer")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvancedPlayerTest {
    @Test @Order(1)
    @DisplayName("fromPath() builds player")
    void buildFromPath() throws Exception {
        try (AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build()) {
            assertNotNull(p);
        }
    }

    @Test @Order(2)
    @DisplayName("fromFile() builds player")
    void buildFromFile() throws Exception {
        try (AdvancedPlayer p = AdvancedPlayer.fromFile(TEST_MP3_PATH.toString())
                .audioDevice(silentDevice()).build()) {
            assertNotNull(p);
        }
    }

    @Test @Order(3)
    @DisplayName("fromStream() builds player")
    void buildFromStream() throws Exception {
        try (InputStream is = TEST_MP3_PATH.toUri().toURL().openStream();
             AdvancedPlayer p = AdvancedPlayer.fromStream(is)
                     .audioDevice(silentDevice()).build()) {
            assertNotNull(p);
        }
    }

    @Test @Order(10)
    @DisplayName("getInfo() returns valid Mp3Info")
    void getInfo() throws Exception {
        try (AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build()) {
            Mp3Info info = p.getInfo();
            assertNotNull(info);
            assertTrue(info.getSampleRate() > 0);
            int ch = info.getChannels();
            assertTrue(ch == 1 || ch == 2, "Expected 1 or 2 channels, got " + ch);
        }
    }

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

        try (AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
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
        holder[0] = AdvancedPlayer.fromPath(TEST_MP3_PATH)
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

        try (AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
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

        try (AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
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
        try (AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
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
                () -> AdvancedPlayer.fromPath(TEST_MP3_PATH).fromFrame(-1));
    }

    @Test @Order(26)
    @DisplayName("Negative endFrame throws IllegalArgumentException immediately")
    void negativeEndFrameThrows() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedPlayer.fromPath(TEST_MP3_PATH).toFrame(-1));
    }

    @Test @Order(30)
    @DisplayName("close() is idempotent")
    void closeIdempotent() throws Exception {
        AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build();
        assertDoesNotThrow(p::close);
        assertDoesNotThrow(p::close);
    }

    @Test @Order(31)
    @DisplayName("start() on a closed player throws IllegalStateException")
    void startAfterCloseThrows() throws Exception {
        AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
                .audioDevice(silentDevice()).build();
        p.close();
        assertThrows(IllegalStateException.class, p::start);
    }

    @Test @Order(32)
    @DisplayName("try-with-resources closes player cleanly")
    void tryWithResources() {
        assertDoesNotThrow(() -> {
            try (AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
                    .audioDevice(silentDevice()).build()) {
                p.getInfo(); // AdvancedPlayer caches info separately, no decoder corruption
            }
        });
    }

    @Test @Order(40)
    @DisplayName("ModernPlaybackListener receives at least one event")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void modernListenerReceivesEvents() throws Exception {
        AtomicBoolean got = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch canContinue = new CountDownLatch(1);

        try (AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
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

        try (AdvancedPlayer p = AdvancedPlayer.fromPath(TEST_MP3_PATH)
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
