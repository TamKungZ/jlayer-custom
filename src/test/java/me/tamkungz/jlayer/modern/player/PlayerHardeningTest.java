package me.tamkungz.jlayer.modern.player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static me.tamkungz.jlayer.modern.TestSupport.TEST_MP3_PATH;
import static me.tamkungz.jlayer.modern.TestSupport.silentDevice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import me.tamkungz.jlayer.modern.Mp3Exception;
import me.tamkungz.jlayer.modern.Mp3Frame;

@DisplayName("Player hardening")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PlayerHardeningTest {

    @Test
    @DisplayName("player exceptions are Mp3Exceptions")
    void exceptionHierarchy() {
        assertTrue(Mp3Exception.class.isAssignableFrom(Mp3PlayerException.class));
        assertTrue(Mp3Exception.class.isAssignableFrom(AdvancedPlayerException.class));
    }

    @Test
    @DisplayName("every frame written to the device carries samples")
    void devicesReceiveSamples() throws Exception {
        AtomicInteger empty = new AtomicInteger();
        AtomicInteger frames = new AtomicInteger();
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH).audioDevice(silentDevice())
                .listener(new Mp3Player.Listener() {
                    @Override public void onFramePlayed(Mp3Frame frame) {
                        frames.incrementAndGet();
                        if (frame.getSampleCount() == 0) {
                            empty.incrementAndGet();
                        }
                    }
                }).build()) {
            p.playAndWait();
        }
        assertTrue(frames.get() > 0);
        assertEquals(0, empty.get());
    }

    @Test
    @DisplayName("awaitTermination() reports completion, and false before start")
    void awaitTermination() throws Exception {
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH).audioDevice(silentDevice()).build()) {
            assertFalse(p.awaitTermination(10, TimeUnit.MILLISECONDS));
            p.play();
            assertTrue(p.awaitTermination(20, TimeUnit.SECONDS));
            assertEquals(Mp3Player.State.STOPPED, p.getState());
        }
    }

    @Test
    @DisplayName("a throwing listener does not interrupt playback")
    void listenerExceptionIsContained() throws Exception {
        AtomicBoolean finished = new AtomicBoolean();
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH).audioDevice(silentDevice())
                .listener(new Mp3Player.Listener() {
                    @Override public void onFramePlayed(Mp3Frame frame) {
                        throw new IllegalStateException("listener bug");
                    }

                    @Override public void onPlaybackFinished() {
                        finished.set(true);
                    }
                }).build()) {
            p.playAndWait();
            assertNull(p.getLastError());
        }
        assertTrue(finished.get());
    }

    @Test
    @DisplayName("stop() called from a listener on the playback thread does not hang")
    void stopFromListener() throws Exception {
        Mp3Player[] holder = new Mp3Player[1];
        AtomicBoolean stopped = new AtomicBoolean();
        Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH).audioDevice(silentDevice())
                .listener(new Mp3Player.Listener() {
                    @Override public void onFramePlayed(Mp3Frame frame) {
                        if (frame.getFrameNumber() == 3) {
                            holder[0].stop();
                            stopped.set(true);
                        }
                    }
                }).build();
        holder[0] = p;
        try (p) {
            p.playAndWait();
            assertTrue(stopped.get());
            assertEquals(Mp3Player.State.STOPPED, p.getState());
        }
    }

    @Test
    @DisplayName("close() is idempotent and ends in CLOSED")
    void closeTwice() throws Exception {
        Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH).audioDevice(silentDevice()).build();
        p.close();
        p.close();
        assertEquals(Mp3Player.State.CLOSED, p.getState());
        assertThrows(IllegalStateException.class, p::play);
    }

    @Test
    @DisplayName("state changes are reported once each")
    void stateChangesNotDuplicated() throws Exception {
        StringBuilder states = new StringBuilder();
        try (Mp3Player p = Mp3Player.fromPath(TEST_MP3_PATH).audioDevice(silentDevice())
                .listener(new Mp3Player.Listener() {
                    @Override public void onStateChanged(Mp3Player.State oldState, Mp3Player.State newState) {
                        synchronized (states) {
                            states.append(oldState).append("->").append(newState).append(' ');
                        }
                    }
                }).build()) {
            p.playAndWait();
        }
        synchronized (states) {
            assertEquals("STOPPED->PLAYING PLAYING->STOPPED STOPPED->CLOSED ", states.toString());
        }
    }

    @Test
    @DisplayName("AdvancedPlayer probes streams that start with a large ID3 tag")
    void advancedPlayerLargeTag(@TempDir Path dir) throws Exception {
        byte[] mp3 = Files.readAllBytes(TEST_MP3_PATH);
        int padding = 200_000;
        byte[] data = new byte[10 + padding + mp3.length];
        System.arraycopy(new byte[] {'I', 'D', '3', 3, 0, 0,
            (byte) ((padding >> 21) & 0x7F), (byte) ((padding >> 14) & 0x7F),
            (byte) ((padding >> 7) & 0x7F), (byte) (padding & 0x7F)}, 0, data, 0, 10);
        System.arraycopy(mp3, 0, data, 10 + padding, mp3.length);

        try (AdvancedPlayer p = AdvancedPlayer.fromStream(new java.io.ByteArrayInputStream(data))
                .audioDevice(silentDevice()).build()) {
            assertNotNull(p.getInfo());
            p.playAndWait();
            assertNull(p.getLastError());
        }
    }

    @Test
    @DisplayName("AdvancedPlayer closes the source when the build fails")
    void advancedPlayerBuildFailureClosesSource() {
        boolean[] closed = new boolean[1];
        java.io.InputStream broken = new java.io.ByteArrayInputStream(new byte[64]) {
            @Override public void close() {
                closed[0] = true;
            }
        };
        assertThrows(java.io.IOException.class, () -> AdvancedPlayer.fromStream(broken)
                .audioDevice(silentDevice()).build());
        assertTrue(closed[0]);
    }
}
