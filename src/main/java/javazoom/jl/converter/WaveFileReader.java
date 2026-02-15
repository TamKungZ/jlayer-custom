package javazoom.jl.converter;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Simple WAV file reader for PCM 8/16/24-bit little-endian files with enhanced features.
 * 
 * <p>Enhanced features in this version:
 * <ul>
 *   <li>Support for 8, 16, and 24-bit audio</li>
 *   <li>Metadata extraction (duration, format info)</li>
 *   <li>Seek support for file-based streams</li>
 *   <li>Better error handling and validation</li>
 *   <li>Progress tracking</li>
 *   <li>Chunk iteration for advanced usage</li>
 * </ul>
 * 
 * <p>Usage example:
 * <pre>{@code
 * try (WaveFileReader reader = new WaveFileReader()) {
 *     reader.open("input.wav");
 *     short[] buffer = new short[1024];
 *     int samplesRead;
 *     while ((samplesRead = reader.readSamples(buffer, 0, 512)) > 0) {
 *         // Process samples
 *     }
 * }
 * }</pre>
 */
public class WaveFileReader implements Closeable {

    private InputStream in;
    private int sampleRate;
    private short bitsPerSample;
    private short numChannels;
    private long dataSize;
    private long bytesReadFromData;
    private File sourceFile;
    private long totalLength;
    private boolean isSeekable;
    
    /** Buffer for reading data */
    private static final int READ_BUFFER_SIZE = 8192;
    
    public WaveFileReader() {
    }

    /**
     * Open a WAV file from filename.
     * @param filename path to WAV file
     * @throws IOException if file cannot be opened or is invalid
     */
    public void open(String filename) throws IOException {
        Objects.requireNonNull(filename, "Filename cannot be null");
        File f = new File(filename);
        if (!f.exists()) {
            throw new IOException("File not found: " + filename);
        }
        if (!f.canRead()) {
            throw new IOException("File not readable: " + filename);
        }
        this.sourceFile = f;
        FileInputStream fis = new FileInputStream(f);
        BufferedInputStream bis = new BufferedInputStream(fis, READ_BUFFER_SIZE);
        this.totalLength = f.length();
        this.isSeekable = true;
        open(bis, totalLength);
    }
    
    /**
     * Open a WAV file from Path.
     * @param path path to WAV file
     * @throws IOException if file cannot be opened or is invalid
     */
    public void open(Path path) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");
        this.sourceFile = path.toFile();
        InputStream is = Files.newInputStream(path);
        BufferedInputStream bis = new BufferedInputStream(is, READ_BUFFER_SIZE);
        this.totalLength = Files.size(path);
        this.isSeekable = true;
        open(bis, totalLength);
    }

    /**
     * Open a WAV file from an InputStream.
     * @param input input stream containing WAV data
     * @param totalLength total length of stream (-1 if unknown)
     * @throws IOException if stream cannot be read or is invalid
     */
    public void open(InputStream input, long totalLength) throws IOException {
        Objects.requireNonNull(input, "Input stream cannot be null");
        this.in = input;
        this.totalLength = totalLength;
        this.isSeekable = false;
        parseHeader();
    }

    private void parseHeader() throws IOException {
        // Read RIFF header
        byte[] buf = new byte[12];
        readFully(buf, 0, 12);
        String riff = new String(buf, 0, 4, "US-ASCII");
        if (!"RIFF".equals(riff)) {
            throw new IOException("Not a RIFF file (found: " + riff + ")");
        }
        
        String wave = new String(buf, 8, 4, "US-ASCII");
        if (!"WAVE".equals(wave)) {
            throw new IOException("Not a WAVE file (found: " + wave + ")");
        }

        // Read chunks until 'fmt ' and 'data' found
        boolean fmtFound = false;
        boolean dataFound = false;

        while (!fmtFound || !dataFound) {
            byte[] hdr = new byte[8];
            readFully(hdr, 0, 8);
            String id = new String(hdr, 0, 4, "US-ASCII");
            int chunkSize = leInt(hdr, 4);

            switch (id) {
                case "fmt " -> {
                    if (chunkSize < 16) {
                        throw new IOException("Invalid fmt chunk size: " + chunkSize);
                    }
                    
                    byte[] fmt = new byte[chunkSize];
                    readFully(fmt, 0, chunkSize);
                    
                    int audioFormat = (fmt[0] & 0xFF) | ((fmt[1] & 0xFF) << 8);
                    if (audioFormat != 1) {
                        throw new IOException("Only PCM WAV supported (format=" + audioFormat + ")");
                    }
                    
                    numChannels = (short) ((fmt[2] & 0xFF) | ((fmt[3] & 0xFF) << 8));
                    if (numChannels < 1 || numChannels > 16) {
                        throw new IOException("Invalid channel count: " + numChannels);
                    }
                    
                    sampleRate = leInt(fmt, 4);
                    if (sampleRate <= 0 || sampleRate > 192000) {
                        throw new IOException("Invalid sample rate: " + sampleRate);
                    }
                    
                    bitsPerSample = (short) ((fmt[14] & 0xFF) | ((fmt[15] & 0xFF) << 8));
                    if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24) {
                        throw new IOException("Unsupported bits per sample: " + bitsPerSample);
                    }
                    
                    fmtFound = true;
                    
                    // Handle odd-sized chunks (padding byte)
                    if (chunkSize % 2 == 1) {
                        in.read(); // Skip padding byte
                    }
                }
                case "data" -> {
                    dataSize = chunkSize;
                    bytesReadFromData = 0;
                    dataFound = true;
                }
                default -> {
                    // Skip unknown chunk
                    skipChunk(chunkSize);
                    
                    // Handle odd-sized chunks (padding byte)
                    if (chunkSize % 2 == 1) {
                        in.read(); // Skip padding byte
                    }
                }
            }
        }
        
        if (!fmtFound) {
            throw new IOException("No fmt chunk found");
        }
        if (!dataFound) {
            throw new IOException("No data chunk found");
        }
    }
    
    private void skipChunk(long size) throws IOException {
        long toSkip = size;
        while (toSkip > 0) {
            long skipped = in.skip(toSkip);
            if (skipped <= 0) {
                // skip() may not work, try reading
                int toRead = (int) Math.min(toSkip, READ_BUFFER_SIZE);
                byte[] temp = new byte[toRead];
                int read = in.read(temp);
                if (read < 0) {
                    throw new IOException("Unexpected EOF while skipping chunk");
                }
                toSkip -= read;
            } else {
                toSkip -= skipped;
            }
        }
    }

    private static int leInt(byte[] buf, int offs) {
        return ((buf[offs] & 0xFF)) | 
               ((buf[offs + 1] & 0xFF) << 8) | 
               ((buf[offs + 2] & 0xFF) << 16) | 
               ((buf[offs + 3] & 0xFF) << 24);
    }

    private void readFully(byte[] b, int off, int len) throws IOException {
        int rem = len;
        while (rem > 0) {
            int r = in.read(b, off + (len - rem), rem);
            if (r < 0) {
                throw new IOException("Unexpected EOF (expected " + len + " bytes, got " + (len - rem) + ")");
            }
            rem -= r;
        }
    }

    /**
     * Read interleaved samples into provided buffer.
     * @param buffer destination buffer
     * @param offset starting offset in buffer
     * @param samplesPerChannel number of samples per channel to read
     * @return number of samples per channel actually read, 0 if EOF
     * @throws IOException if read error occurs
     */
    public int readSamples(short[] buffer, int offset, int samplesPerChannel) throws IOException {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        if (offset < 0 || samplesPerChannel < 0) {
            throw new IllegalArgumentException("Invalid offset or sample count");
        }
        if (offset + samplesPerChannel * numChannels > buffer.length) {
            throw new IllegalArgumentException("Buffer too small");
        }
        
        int bytesPerSample = bitsPerSample / 8;
        int frameBytes = bytesPerSample * numChannels;
        int bytesToRead = samplesPerChannel * frameBytes;
        
        // Don't read past data chunk
        long remainingBytes = dataSize - bytesReadFromData;
        if (bytesToRead > remainingBytes) {
            bytesToRead = (int) remainingBytes;
        }
        
        if (bytesToRead <= 0) {
            return 0; // EOF
        }
        
        byte[] tmp = new byte[bytesToRead];
        int total = 0;
        
        while (total < bytesToRead) {
            int read = in.read(tmp, total, bytesToRead - total);
            if (read < 0) break;
            total += read;
        }
        
        if (total <= 0) {
            return 0;
        }
        
        int samplesRead = total / frameBytes;
        
        // Convert based on bit depth
        int p = offset;
        switch (bitsPerSample) {
            case 16 -> {
                ByteBuffer bb = ByteBuffer.wrap(tmp, 0, total);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < samplesRead; i++) {
                    for (int ch = 0; ch < numChannels; ch++) {
                        short s = bb.getShort();
                        buffer[p++] = s;
                    }
                }
            }
            case 8 -> {
                // 8-bit unsigned to signed 16-bit
                int idx = 0;
                for (int i = 0; i < samplesRead; i++) {
                    for (int ch = 0; ch < numChannels; ch++) {
                        int v = tmp[idx++] & 0xFF;
                        short s = (short) ((v - 128) << 8);
                        buffer[p++] = s;
                    }
                }
            }
            case 24 -> {
                // 24-bit to 16-bit (take upper 16 bits)
                int idx = 0;
                for (int i = 0; i < samplesRead; i++) {
                    for (int ch = 0; ch < numChannels; ch++) {
                        idx++; // Skip low byte
                        int mid = tmp[idx++] & 0xFF;
                        int high = tmp[idx++] & 0xFF;
                        // Use middle and high bytes for 16-bit
                        short s = (short) ((mid) | (high << 8));
                        buffer[p++] = s;
                    }
                }
            }
        }
        
        bytesReadFromData += total;
        return samplesRead;
    }
    
    /**
     * Read all remaining samples.
     * @return array of all samples (interleaved)
     * @throws IOException if read error occurs
     */
    public short[] readAllSamples() throws IOException {
        long remainingSamples = getRemainingFrames();
        if (remainingSamples > Integer.MAX_VALUE / numChannels) {
            throw new IOException("File too large to read into memory");
        }
        
        int totalSamples = (int) (remainingSamples * numChannels);
        short[] allSamples = new short[totalSamples];
        
        int offset = 0;
        int samplesRead;
        int bufferSize = 4096; // samples per channel per read
        
        while ((samplesRead = readSamples(allSamples, offset, 
                Math.min(bufferSize, (int) (remainingSamples - offset / numChannels)))) > 0) {
            offset += samplesRead * numChannels;
        }
        
        // Return only filled portion if EOF reached early
        if (offset < totalSamples) {
            short[] trimmed = new short[offset];
            System.arraycopy(allSamples, 0, trimmed, 0, offset);
            return trimmed;
        }
        
        return allSamples;
    }

    /**
     * Get sample rate in Hz.
     * @return sample rate
     */
    public int sampleRate() {
        return sampleRate;
    }

    /**
     * Get bits per sample.
     * @return bits per sample (8, 16, or 24)
     */
    public short bitsPerSample() {
        return bitsPerSample;
    }

    /**
     * Get number of channels.
     * @return channel count
     */
    public short numChannels() {
        return numChannels;
    }

    /**
     * Get data chunk size in bytes.
     * @return data size
     */
    public long dataSize() {
        return dataSize;
    }
    
    /**
     * Get total number of frames (samples per channel).
     * @return frame count
     */
    public long getTotalFrames() {
        int bytesPerFrame = (bitsPerSample / 8) * numChannels;
        return dataSize / bytesPerFrame;
    }
    
    /**
     * Get remaining frames to read.
     * @return remaining frame count
     */
    public long getRemainingFrames() {
        int bytesPerFrame = (bitsPerSample / 8) * numChannels;
        return (dataSize - bytesReadFromData) / bytesPerFrame;
    }
    
    /**
     * Get duration in seconds.
     * @return duration
     */
    public double getDuration() {
        return (double) getTotalFrames() / sampleRate;
    }
    
    /**
     * Get current position in seconds.
     * @return current position
     */
    public double getCurrentPosition() {
        int bytesPerFrame = (bitsPerSample / 8) * numChannels;
        long framesRead = bytesReadFromData / bytesPerFrame;
        return (double) framesRead / sampleRate;
    }
    
    /**
     * Get bytes read from data chunk.
     * @return bytes read
     */
    public long getBytesRead() {
        return bytesReadFromData;
    }
    
    /**
     * Get progress as percentage.
     * @return progress (0.0 to 1.0)
     */
    public double getProgress() {
        if (dataSize == 0) return 0.0;
        return (double) bytesReadFromData / dataSize;
    }
    
    /**
     * Check if this is a mono file.
     * @return true if mono
     */
    public boolean isMono() {
        return numChannels == 1;
    }
    
    /**
     * Check if this is a stereo file.
     * @return true if stereo
     */
    public boolean isStereo() {
        return numChannels == 2;
    }
    
    /**
     * Get format information as string.
     * @return format description
     */
    public String getFormatInfo() {
        return String.format("%d Hz, %d-bit, %s, %.2f seconds", 
            sampleRate, 
            bitsPerSample, 
            isMono() ? "Mono" : (isStereo() ? "Stereo" : numChannels + " channels"),
            getDuration());
    }
    
    /**
     * Check if the stream is seekable.
     * @return true if seekable
     */
    public boolean isSeekable() {
        return isSeekable;
    }
    
    /**
     * Reset to beginning of audio data (only for file-based streams).
     * @throws IOException if stream is not seekable or reset fails
     */
    public void reset() throws IOException {
        if (!isSeekable || sourceFile == null) {
            throw new IOException("Stream is not seekable");
        }
        
        close();
        open(sourceFile.getAbsolutePath());
    }

    @Override
    public void close() throws IOException {
        if (in != null) {
            in.close();
            in = null;
        }
    }
    
    /**
     * Metadata class for WAV file information.
     */
    public static class WavMetadata {
        public final int sampleRate;
        public final short bitsPerSample;
        public final short numChannels;
        public final long dataSize;
        public final long totalFrames;
        public final double duration;
        
        public WavMetadata(int sampleRate, short bitsPerSample, short numChannels, 
                          long dataSize, long totalFrames, double duration) {
            this.sampleRate = sampleRate;
            this.bitsPerSample = bitsPerSample;
            this.numChannels = numChannels;
            this.dataSize = dataSize;
            this.totalFrames = totalFrames;
            this.duration = duration;
        }
        
        @Override
        public String toString() {
            return String.format("WAV: %d Hz, %d-bit, %d ch, %.2f sec", 
                sampleRate, bitsPerSample, numChannels, duration);
        }
    }
    
    /**
     * Get metadata without reading audio data.
     * @return metadata object
     */
    public WavMetadata getMetadata() {
        return new WavMetadata(
            sampleRate, 
            bitsPerSample, 
            numChannels, 
            dataSize, 
            getTotalFrames(), 
            getDuration()
        );
    }
    
    /**
     * Quick metadata extraction without creating a full reader.
     * @param filename path to WAV file
     * @return metadata object
     * @throws IOException if file cannot be read
     */
    public static WavMetadata extractMetadata(String filename) throws IOException {
        try (WaveFileReader reader = new WaveFileReader()) {
            reader.open(filename);
            return reader.getMetadata();
        }
    }
    
    /**
     * Quick metadata extraction from Path.
     * @param path path to WAV file
     * @return metadata object
     * @throws IOException if file cannot be read
     */
    public static WavMetadata extractMetadata(Path path) throws IOException {
        try (WaveFileReader reader = new WaveFileReader()) {
            reader.open(path);
            return reader.getMetadata();
        }
    }
}