package me.tamkungz.jlayer.modern;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.NullAudioDevice;
import javazoom.jl.player.NullAudioDeviceFactory;

/** Shared fixtures for the modern API tests. */
public final class TestSupport {

    /** The MP3 named by {@code test.mp3.properties}. */
    public static final Path TEST_MP3_PATH = locateTestMp3();

    private TestSupport() {}

    private static Path locateTestMp3() {
        Properties props = new Properties();
        try (InputStream in = TestSupport.class.getClassLoader()
                .getResourceAsStream("test.mp3.properties")) {
            if (in == null) {
                throw new IllegalStateException("test.mp3.properties must be on the test classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path path = Paths.get(props.getProperty("basefile", "") + props.getProperty("filename", ""));
        if (!path.toFile().exists()) {
            throw new IllegalStateException("Test MP3 file not found: " + path);
        }
        return path;
    }

    /** Returns a silent device so tests never emit actual audio. */
    public static AudioDevice silentDevice() {
        try {
            return FactoryRegistry.systemRegistry().createAudioDevice(NullAudioDeviceFactory.class);
        } catch (Exception e) {
            return new NullAudioDevice();
        }
    }

    /** Waits up to {@code timeoutMs} for {@code thread} to be alive. */
    public static boolean waitForThreadAlive(Thread thread, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!thread.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return thread.isAlive();
    }
}
