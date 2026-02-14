package javazoom.jl.converter;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Simple WAV file reader for PCM 8/16-bit little-endian files.
 */
public class WaveFileReader implements Closeable {

    private InputStream in;
    private int sampleRate;
    private short bitsPerSample;
    private short numChannels;
    private long dataStart;
    private long dataSize;
    private long bytesReadFromData;

    public WaveFileReader() {
    }

    public void open(String filename) throws IOException {
        File f = new File(filename);
        FileInputStream fis = new FileInputStream(f);
        open(fis, f.length());
    }

    public void open(InputStream input, long totalLength) throws IOException {
        this.in = input;
        parseHeader();
    }

    private void parseHeader() throws IOException {
        // Read RIFF header
        byte[] buf = new byte[12];
        readFully(buf, 0, 12);
        String riff = new String(buf, 0, 4, "US-ASCII");
        if (!"RIFF".equals(riff)) throw new IOException("Not a RIFF file");
        // int fileSize = leInt(buf, 4); // not used
        String wave = new String(buf, 8, 4, "US-ASCII");
        if (!"WAVE".equals(wave)) throw new IOException("Not a WAVE file");

        // Read chunks until 'fmt ' and 'data' found
        boolean fmtFound = false;
        boolean dataFound = false;

        while (!fmtFound || !dataFound) {
            byte[] hdr = new byte[8];
            readFully(hdr, 0, 8);
            String id = new String(hdr, 0, 4, "US-ASCII");
            int chunkSize = leInt(hdr, 4);

            if ("fmt ".equals(id)) {
                byte[] fmt = new byte[chunkSize];
                readFully(fmt, 0, chunkSize);
                int audioFormat = (fmt[0] & 0xFF) | ((fmt[1] & 0xFF) << 8);
                if (audioFormat != 1) throw new IOException("Only PCM WAV supported (format=" + audioFormat + ")");
                numChannels = (short) ((fmt[2] & 0xFF) | ((fmt[3] & 0xFF) << 8));
                sampleRate = leInt(fmt, 4);
                bitsPerSample = (short) ((fmt[14] & 0xFF) | ((fmt[15] & 0xFF) << 8));
                fmtFound = true;
                // if chunkSize is odd, there is a pad byte; handled by next chunk read
            } else if ("data".equals(id)) {
                dataSize = chunkSize;
                dataStart = -1; // not tracked for InputStream; bytesReadFromData=0
                bytesReadFromData = 0;
                dataFound = true;
                break; // data follows immediately
            } else {
                // skip uninterested chunk
                long toSkip = chunkSize;
                while (toSkip > 0) {
                    long skipped = in.skip(toSkip);
                    if (skipped <= 0) throw new IOException("Failed to skip chunk");
                    toSkip -= skipped;
                }
            }
        }
    }

    private static int leInt(byte[] buf, int offs) {
        return ((buf[offs] & 0xFF)) | ((buf[offs + 1] & 0xFF) << 8) | ((buf[offs + 2] & 0xFF) << 16) | ((buf[offs + 3] & 0xFF) << 24);
    }

    private void readFully(byte[] b, int off, int len) throws IOException {
        int rem = len;
        while (rem > 0) {
            int r = in.read(b, off + (len - rem), rem);
            if (r < 0) throw new IOException("Unexpected EOF");
            rem -= r;
        }
    }

    /**
     * Read interleaved samples into provided buffer. Returns number of samples per channel read.
     */
    public int readSamples(short[] buffer, int offset, int samplesPerChannel) throws IOException {
        if (bitsPerSample != 16 && bitsPerSample != 8) throw new IOException("Only 8 or 16 bit supported");
        int bytesPerSample = bitsPerSample / 8;
        int frameBytes = bytesPerSample * numChannels;
        int bytesToRead = samplesPerChannel * frameBytes;
        byte[] tmp = new byte[bytesToRead];
        int read = 0;
        int total = 0;
        while (total < bytesToRead) {
            read = in.read(tmp, total, bytesToRead - total);
            if (read < 0) break;
            total += read;
        }
        if (total <= 0) return 0;
        int samplesRead = total / frameBytes;
        // convert
        int p = offset;
        if (bitsPerSample == 16) {
            ByteBuffer bb = ByteBuffer.wrap(tmp);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samplesRead; i++) {
                for (int ch = 0; ch < numChannels; ch++) {
                    short s = bb.getShort();
                    buffer[p++] = s;
                }
            }
        } else { // 8-bit unsigned
            int idx = 0;
            for (int i = 0; i < samplesRead; i++) {
                for (int ch = 0; ch < numChannels; ch++) {
                    int v = tmp[idx++] & 0xFF;
                    short s = (short) ((v - 128) << 8);
                    buffer[p++] = s;
                }
            }
        }
        bytesReadFromData += total;
        return samplesRead;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public short bitsPerSample() {
        return bitsPerSample;
    }

    public short numChannels() {
        return numChannels;
    }

    public long dataSize() {
        return dataSize;
    }

    @Override
    public void close() throws IOException {
        if (in != null) {
            in.close();
            in = null;
        }
    }
}
