package me.tamkungz.jlayer.modern.player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import me.tamkungz.jlayer.modern.Mp3Decoder;
import me.tamkungz.jlayer.modern.Mp3Frame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static me.tamkungz.jlayer.modern.TestSupport.TEST_MP3_PATH;
import static me.tamkungz.jlayer.modern.TestSupport.silentDevice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Decoder and player working together. */
@DisplayName("Integration")
class ModernApiIntegrationTest {
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
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            decoderFrames = d.decodeAll().size();
        }

        AtomicInteger playerFrames = new AtomicInteger();
        AtomicReference<Throwable> err = new AtomicReference<>();
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
            c1 = d.decodeAll().size();
        }
        try (Mp3Decoder d = Mp3Decoder.fromPath(TEST_MP3_PATH)) {
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

        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH)
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
