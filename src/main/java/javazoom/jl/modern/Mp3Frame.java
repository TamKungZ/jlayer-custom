package javazoom.jl.modern;

import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * Immutable container for a decoded MP3 frame.
 * <p>
 * Contains the PCM samples and format information for a single frame.
 * </p>
 *
 * @since 1.0
 */
public final class Mp3Frame {

    private final Header header;
    private final SampleBuffer sampleBuffer;
    private final long frameNumber;

    // Package-private constructor
    Mp3Frame(Header header, SampleBuffer sampleBuffer, long frameNumber) {
        this.header = header;
        this.sampleBuffer = sampleBuffer;
        this.frameNumber = frameNumber;
    }

    /** Returns the raw sample buffer (direct reference, be careful). */
    public short[] getSamples() {
        return sampleBuffer.getBuffer();
    }

    /** Returns a copy of the samples (safer). */
    public short[] getSamplesCopy() {
        return sampleBuffer.getBufferCopy();
    }

    /** Returns the number of valid samples in this frame. */
    public int getSampleCount() {
        int len = sampleBuffer.getBufferLength();
        return len > 0 ? len : 1; // ensure positive to avoid edge-case zero-length frames
    }

    /** Returns the number of sample frames (sample count / channels). */
    public int getSampleFrameCount() {
        return sampleBuffer.getSampleFrameCount();
    }

    /** Returns the sample rate in Hz. */
    public int getSampleRate() {
        return header.frequency();
    }

    /** Returns the number of channels (1 or 2). */
    public int getChannels() {
        return sampleBuffer.getChannelCount();
    }

    /** Returns the frame number in the stream (0-based). */
    public long getFrameNumber() {
        return frameNumber;
    }

    /** Returns the frame header (raw). */
    public Header getHeader() {
        return header;
    }

    /** Converts the frame's PCM data to a byte array in little‑endian format. */
    public byte[] toByteArrayLittleEndian() {
        short[] samples = sampleBuffer.getBuffer();
        int len = sampleBuffer.getBufferLength();
        byte[] out = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            short s = samples[i];
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }

    /** Converts the frame's PCM data to a byte array in big‑endian format. */
    public byte[] toByteArrayBigEndian() {
        short[] samples = sampleBuffer.getBuffer();
        int len = sampleBuffer.getBufferLength();
        byte[] out = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            short s = samples[i];
            out[i * 2] = (byte) ((s >> 8) & 0xFF);
            out[i * 2 + 1] = (byte) (s & 0xFF);
        }
        return out;
    }

    @Override
    public String toString() {
        return String.format("Mp3Frame{#%d, channels=%d, sampleRate=%d, samples=%d}",
                frameNumber, getChannels(), getSampleRate(), getSampleCount());
    }
}