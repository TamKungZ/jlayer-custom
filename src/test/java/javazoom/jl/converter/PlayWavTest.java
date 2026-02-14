package javazoom.jl.converter;

import java.io.File;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class PlayWavTest {

    @Test
    public void playAbletonWav() throws Exception {
        File wav = new File("src/test/resources/c-major-scale _test_ableton-live.wav");
        Assumptions.assumeTrue(wav.exists(), "Test WAV file not present, skipping playback test");

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(wav)) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.start();
            // play for up to 3 seconds then stop
            Thread.sleep(3000);
            clip.stop();
            clip.close();
        }

        assertTrue(true);
    }
}
