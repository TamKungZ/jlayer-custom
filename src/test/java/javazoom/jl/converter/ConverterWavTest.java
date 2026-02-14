package javazoom.jl.converter;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class ConverterWavTest {

    @Test
    public void convertMp3ToWav() throws Exception {
        String base = "src/test/resources/";
        String source = base + "c-major-scale _test_audacity.mp3";
        File out = new File("target/test-output-audacity.wav");
        if (out.exists()) out.delete();

        Converter conv = new Converter();
        conv.convert(source, out.getAbsolutePath());

        assertTrue(out.exists(), "Output WAV should exist");
        assertTrue(out.length() > 44, "Output WAV should have data");
    }
}
