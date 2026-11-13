package me.tamkungz.jlayer.modern;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Mp3Tags")
class Mp3TagsTest {

    private static byte[] frame23(String id, byte[]... parts) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            body.writeBytes(part);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(id.getBytes(StandardCharsets.ISO_8859_1));
        int size = body.size();
        out.writeBytes(new byte[] {(byte) (size >>> 24), (byte) (size >>> 16), (byte) (size >>> 8), (byte) size, 0, 0});
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] text(int encoding, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(encoding);
        out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] tag(int major, byte[]... frames) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] frame : frames) {
            body.writeBytes(frame);
        }
        body.writeBytes(new byte[16]);
        int size = body.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {'I', 'D', '3', (byte) major, 0, 0,
            (byte) ((size >> 21) & 0x7F), (byte) ((size >> 14) & 0x7F), (byte) ((size >> 7) & 0x7F), (byte) (size & 0x7F)});
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] latin1(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("reads ISO-8859-1, UTF-16 and UTF-8 text frames of an ID3v2.3 tag")
    void readsTextFrames() throws IOException {
        byte[] utf16 = new byte[] {(byte) 0xFF, (byte) 0xFE};
        byte[] data = tag(3,
                frame23("TIT2", text(0, latin1("Song"))),
                frame23("TPE1", text(1, concat(utf16, "Artist ก".getBytes(StandardCharsets.UTF_16LE)))),
                frame23("TALB", text(3, "Album é".getBytes(StandardCharsets.UTF_8))),
                frame23("TYER", text(0, latin1("2024"))),
                frame23("TRCK", text(0, latin1("3/12"))),
                frame23("TCON", text(0, latin1("Rock"))));

        Mp3Tags tags = Mp3Tags.read(new ByteArrayInputStream(data));

        assertEquals("Song", tags.getTitle().orElseThrow());
        assertEquals("Artist ก", tags.getArtist().orElseThrow());
        assertEquals("Album é", tags.getAlbum().orElseThrow());
        assertEquals("2024", tags.getYear().orElseThrow());
        assertEquals("3/12", tags.getTrack().orElseThrow());
        assertEquals("Rock", tags.getGenre().orElseThrow());
        assertFalse(tags.isEmpty());
    }

    @Test
    @DisplayName("prefers the front cover picture and returns defensive copies")
    void readsCoverArt() throws IOException {
        byte[] other = frame23("APIC", text(0, concat(latin1("image/png"), new byte[] {0, 0, 0}, new byte[] {1, 2, 3})));
        byte[] front = frame23("APIC", text(0, concat(latin1("image/jpeg"), new byte[] {0, 3, 0}, new byte[] {9, 8, 7, 6})));

        Mp3Tags tags = Mp3Tags.read(new ByteArrayInputStream(tag(3, other, front)));

        Mp3Tags.CoverArt art = tags.getCoverArt().orElseThrow();
        assertEquals("image/jpeg", art.mimeType());
        assertArrayEquals(new byte[] {9, 8, 7, 6}, art.data());
        assertNotSame(art.data(), art.data());
    }

    @Test
    @DisplayName("reads ID3v2.2 three-letter frames")
    void readsV22() throws IOException {
        byte[] title = concat(new byte[] {'T', 'T', '2', 0, 0, 5}, text(0, latin1("Old")), new byte[0]);
        title[5] = (byte) (1 + 3);
        Mp3Tags tags = Mp3Tags.read(new ByteArrayInputStream(tag(2, title)));
        assertEquals("Old", tags.getTitle().orElseThrow());
    }

    @Test
    @DisplayName("falls back to the ID3v1 tag at the end of a file")
    void readsV1(@TempDir Path dir) throws IOException {
        byte[] v1 = new byte[128];
        System.arraycopy(latin1("TAG"), 0, v1, 0, 3);
        System.arraycopy(latin1("Legacy Title"), 0, v1, 3, 12);
        System.arraycopy(latin1("Legacy Artist"), 0, v1, 33, 13);
        System.arraycopy(latin1("1999"), 0, v1, 93, 4);
        v1[126] = 7;
        Path file = dir.resolve("v1.mp3");
        Files.write(file, concat(new byte[300], v1));

        Mp3Tags tags = Mp3Tags.read(file);

        assertEquals("Legacy Title", tags.getTitle().orElseThrow());
        assertEquals("Legacy Artist", tags.getArtist().orElseThrow());
        assertEquals("1999", tags.getYear().orElseThrow());
        assertEquals("7", tags.getTrack().orElseThrow());
    }

    @Test
    @DisplayName("ID3v2 values win over ID3v1")
    void v2WinsOverV1(@TempDir Path dir) throws IOException {
        byte[] v1 = new byte[128];
        System.arraycopy(latin1("TAG"), 0, v1, 0, 3);
        System.arraycopy(latin1("V1 Title"), 0, v1, 3, 8);
        System.arraycopy(latin1("V1 Artist"), 0, v1, 33, 9);
        Path file = dir.resolve("both.mp3");
        Files.write(file, concat(tag(3, frame23("TIT2", text(0, latin1("V2 Title")))), new byte[200], v1));

        Mp3Tags tags = Mp3Tags.read(file);

        assertEquals("V2 Title", tags.getTitle().orElseThrow());
        assertEquals("V1 Artist", tags.getArtist().orElseThrow());
    }

    @Test
    @DisplayName("returns EMPTY for data without tags, empty input and tiny files")
    void noTags(@TempDir Path dir) throws IOException {
        assertTrue(Mp3Tags.read(new ByteArrayInputStream(new byte[0])).isEmpty());
        assertTrue(Mp3Tags.read(new ByteArrayInputStream(new byte[64])).isEmpty());
        Path tiny = dir.resolve("tiny.mp3");
        Files.write(tiny, new byte[10]);
        assertTrue(Mp3Tags.read(tiny).isEmpty());
    }

    @Test
    @DisplayName("skips malformed frames instead of failing")
    void malformedFrames() throws IOException {
        byte[] bogusSize = concat(latin1("TIT2"), new byte[] {0x7F, 0x7F, 0x7F, 0x7F, 0, 0}, new byte[4]);
        byte[] data = tag(3, frame23("TPE1", text(0, latin1("Kept"))), bogusSize);

        Mp3Tags tags = Mp3Tags.read(new ByteArrayInputStream(data));

        assertEquals("Kept", tags.getArtist().orElseThrow());
        assertTrue(tags.getTitle().isEmpty());

        byte[] truncated = Arrays.copyOf(tag(3, frame23("TIT2", text(0, latin1("Cut")))), 14);
        assertTrue(Mp3Tags.read(new ByteArrayInputStream(truncated)).isEmpty());
    }

    @Test
    @DisplayName("rejects tag sizes that are not syncsafe")
    void rejectsInvalidSize() throws IOException {
        byte[] data = tag(3, frame23("TIT2", text(0, latin1("X"))));
        data[6] = (byte) 0x80;
        assertTrue(Mp3Tags.read(new ByteArrayInputStream(data)).isEmpty());
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
