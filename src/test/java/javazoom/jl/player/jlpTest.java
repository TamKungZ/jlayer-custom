/*
 * Modernized JLayer Player Test Suite
 * Tests concurrent playback control and resource management
 */

package javazoom.jl.player;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import javazoom.jl.player.my.MyJavaSoundAudioDevice;
import javazoom.jl.player.my.MyJavaSoundAudioDeviceFactory;

/**
 * Modern test suite for JLayer Player.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class jlpTest {

    private static final float VOLUME = (float) Double.parseDouble(
        System.getProperty("vavi.test.volume", "0.2")
    );
    private static final int PLAY_DURATION_MS = 3000;
    
    private String filename;

    @BeforeEach
    void setUp() throws Exception {
        Properties props = new Properties();
        try (InputStream pin = getClass().getClassLoader()
                .getResourceAsStream("test.mp3.properties")) {
            assertNotNull(pin, "test.mp3.properties not found");
            props.load(pin);
        }
        
        String basefile = props.getProperty("basefile");
        String name = props.getProperty("filename");
        filename = basefile + name;
        System.out.println("Test file: " + filename);
    }

    @Test
    @Order(1)
    @DisplayName("Basic playback")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testPlay() throws Exception {
        jlp player = jlp.createInstance(new String[]{filename});
        
        try {
            AudioDevice dev = FactoryRegistry.systemRegistry()
                    .createAudioDevice(JavaSoundAudioDeviceFactory.class);
            Assumptions.assumeFalse(dev instanceof NullAudioDevice);
            player.setAudioDevice(dev);
        } catch (Exception ex) {
            Assumptions.abort("Audio device not available: " + ex.getMessage());
        }
        
        AtomicBoolean stopped = new AtomicBoolean(false);
        Thread stopThread = new Thread(() -> {
            try {
                Thread.sleep(PLAY_DURATION_MS);
                player.stop();
                stopped.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        stopThread.start();
        player.play();
        stopThread.join(5000);
        
        assertTrue(stopped.get());
    }

    @Test
    @Order(2)
    @DisplayName("Volume control")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testPlay2() throws Exception {
        jlp player = jlp.createInstance(new String[]{filename});
        
        AudioDevice ad;
        try {
            ad = player.setAudioDevice();
        } catch (Exception ex) {
            Assumptions.abort("No audio device: " + ex.getMessage());
            return;
        }
        
        Assumptions.assumeTrue(ad instanceof MyJavaSoundAudioDevice);
        ((MyJavaSoundAudioDevice) ad).setVolume(VOLUME);
        
        CountDownLatch latch = new CountDownLatch(1);
        Thread playThread = new Thread(() -> {
            try {
                player.play();
            } catch (Exception e) {
                fail("Playback failed: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        playThread.start();
        Thread.sleep(PLAY_DURATION_MS);
        player.stop();
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @Order(3)
    @DisplayName("Specified device")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testPlay3() throws Exception {
        jlp player = jlp.createInstance(new String[]{filename});
        
        try {
            AudioDevice dev = FactoryRegistry.systemRegistry()
                    .createAudioDevice(MyJavaSoundAudioDeviceFactory.class);
            Assumptions.assumeTrue(dev instanceof MyJavaSoundAudioDevice);
            player.setAudioDevice(dev);
            
            AudioDevice ad = player.setAudioDevice();
            ((MyJavaSoundAudioDevice) ad).setVolume(VOLUME);
        } catch (Exception ex) {
            Assumptions.abort("Device setup failed: " + ex.getMessage());
        }
        
        CountDownLatch latch = new CountDownLatch(1);
        Thread playThread = new Thread(() -> {
            try {
                player.play();
            } catch (Exception e) {
                // Expected when stopped
            } finally {
                latch.countDown();
            }
        });
        
        playThread.start();
        Thread.sleep(PLAY_DURATION_MS);
        player.stop();
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}