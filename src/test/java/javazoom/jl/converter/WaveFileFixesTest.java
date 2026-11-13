package javazoom.jl.converter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import javazoom.jl.decoder.JavaLayerException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WaveFileFixesTest {

    @TempDir
    Path dir;

    private static int riffSize(byte[] b) {
        return ByteBuffer.wrap(b, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private void write(WaveFile wav, Path p, int samples) throws IOException {
        assertEquals(WaveFile.DDC_SUCCESS, wav.openForWrite(p, 44100, (short) 16, (short) 2));
        assertEquals(WaveFile.DDC_SUCCESS, wav.writeData(new short[samples], samples));
        wav.close();
    }

    @Test
    public void riffSizeMatchesFileLength() throws Exception {
        Path p = dir.resolve("a.wav");
        write(new WaveFile(), p, 200);
        byte[] b = Files.readAllBytes(p);
        assertEquals(b.length - 8, riffSize(b));
    }

    @Test
    public void reusingInstanceAndOverwritingStaysConsistent() throws Exception {
        Path p = dir.resolve("b.wav");
        WaveFile wav = new WaveFile();
        write(wav, p, 2000);
        write(wav, p, 200);
        byte[] b = Files.readAllBytes(p);
        assertEquals(44 + 400, b.length);
        assertEquals(b.length - 8, riffSize(b));
    }

    @Test
    public void rejectsPartialFramesAnd24Bit() throws Exception {
        WaveFile wav = new WaveFile();
        assertEquals(WaveFile.DDC_SUCCESS, wav.openForWrite(dir.resolve("c.wav"), 44100, (short) 16, (short) 2));
        assertEquals(WaveFile.DDC_INVALID_CALL, wav.writeData(new short[3], 3));
        wav.close();

        WaveFile wav24 = new WaveFile();
        assertEquals(WaveFile.DDC_SUCCESS, wav24.openForWrite(dir.resolve("d.wav"), 44100, (short) 24, (short) 2));
        assertEquals(WaveFile.DDC_INVALID_CALL, wav24.writeData(new short[2], 2));
        wav24.close();
    }

    @Test
    public void converterRefusesToOverwriteItsSource() throws Exception {
        Path src = dir.resolve("same.mp3");
        Files.copy(Path.of("src/test/resources/c-major-scale_test_audacity.mp3"), src);
        long size = Files.size(src);
        assertThrows(JavaLayerException.class,
            () -> new Converter().convert(src.toString(), src.toString()));
        assertEquals(size, Files.size(src));
    }
}
