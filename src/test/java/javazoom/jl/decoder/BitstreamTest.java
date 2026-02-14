/*
 * Modern Bitstream Test Suite
 * Tests MP3 frame parsing and bitstream operations
 */

package javazoom.jl.decoder;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests Bitstream functionality with actual MP3 files.
 */
class BitstreamTest {

    private String filename;
    private FileInputStream mp3in;
    private Bitstream bitstream;

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
        
        mp3in = new FileInputStream(filename);
        bitstream = new Bitstream(mp3in);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (bitstream != null && !bitstream.isClosed()) {
            bitstream.close();
        }
        if (mp3in != null) {
            mp3in.close();
        }
    }

    @Test
    @DisplayName("Parse MP3 header and validate properties")
    void testHeaderParsing() throws Exception {
        // Read ID3v2 tag if present
        InputStream id3in = bitstream.getRawID3v2();
        int id3Size = (id3in == null) ? 0 : id3in.available();
        
        // Read first frame header
        Header header = bitstream.readFrame();
        assertNotNull(header, "Header should not be null");
        
        // Validate header properties
        assertTrue(header.calculateFrameSize() >= 0, "Frame size should be non-negative");
        assertTrue(header.msPerFrame() > 0.0f, "ms per frame should be positive");
        assertTrue(header.frequency() > 0, "Frequency should be positive");
        assertTrue(header.numberOfSubbands() >= 0, "Subbands should be non-negative");
        
        // Validate bitrate and mode
        assertNotNull(header.bitrateString(), "Bitrate string should not be null");
        assertNotNull(header.modeString(), "Mode string should not be null");
        assertNotNull(header.versionString(), "Version string should not be null");
        
        // Calculate stream properties
        float framesPerSecond = (float) (1000.0 / header.msPerFrame());
        assertTrue(framesPerSecond > 0.0f, "Frames per second should be positive");
        
        float totalMs = header.totalMs(mp3in.available());
        assertTrue(totalMs >= 0.0f, "Total duration should be non-negative");
        
        System.out.println("=== MP3 File Properties ===");
        System.out.println("File: " + filename);
        System.out.println("ID3v2 size: " + id3Size + " bytes");
        System.out.println("Version: " + header.versionString());
        System.out.println("Layer: " + header.layer());
        System.out.println("Bitrate: " + header.bitrateString());
        System.out.println("Sample rate: " + header.sampleFrequencyString());
        System.out.println("Mode: " + header.modeString());
        System.out.println("Frame size: " + header.calculateFrameSize() + " bytes");
        System.out.println("Duration: " + String.format("%.2f", totalMs / 1000.0) + " seconds");
        
        bitstream.closeFrame();
    }

    @Test
    @DisplayName("Read multiple frames sequentially")
    void testMultipleFrames() throws Exception {
        int frameCount = 0;
        int maxFrames = 10;
        
        while (frameCount < maxFrames && !bitstream.isEOF()) {
            Header header = bitstream.readFrame();
            if (header == null) {
                break;
            }
            
            assertNotNull(header, "Header should not be null");
            assertTrue(header.calculateFrameSize() > 0, 
                    "Frame " + frameCount + " should have valid size");
            
            bitstream.closeFrame();
            frameCount++;
        }
        
        assertTrue(frameCount > 0, "Should read at least one frame");
        System.out.println("Successfully read " + frameCount + " frames");
    }

    @Test
    @DisplayName("Bitstream state management")
    void testBitstreamState() throws Exception {
        assertFalse(bitstream.isClosed(), "Bitstream should not be closed initially");
        assertFalse(bitstream.isEOF(), "Should not be at EOF initially");
        
        // Read a frame
        Header header = bitstream.readFrame();
        assertNotNull(header);
        bitstream.closeFrame();
        
        assertFalse(bitstream.isClosed(), "Bitstream should still be open");
        
        // Close and verify
        bitstream.close();
        assertTrue(bitstream.isClosed(), "Bitstream should be closed");
        
        // Closing again should be safe (idempotent)
        assertDoesNotThrow(() -> bitstream.close(), 
                "Multiple close calls should not throw");
    }

    @Test
    @DisplayName("Handle EOF gracefully")
    void testEOFHandling() throws Exception {
        // Read until EOF
        int frameCount = 0;
        while (!bitstream.isEOF()) {
            Header header = bitstream.readFrame();
            if (header == null) {
                break;
            }
            bitstream.closeFrame();
            frameCount++;
            
            // Safety limit
            if (frameCount > 10000) {
                fail("Too many frames, possible infinite loop");
            }
        }
        
        assertTrue(frameCount > 0, "Should have read some frames");
        System.out.println("Total frames read: " + frameCount);
        
        // After EOF, readFrame should return null
        Header header = bitstream.readFrame();
        assertNull(header, "readFrame should return null at EOF");
    }
}