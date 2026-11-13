package me.tamkungz.jlayer.modern;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * A decoded MP3 frame: PCM samples plus the format information of the frame.
 *
 * @since 1.0
 */
public final class Mp3Frame {

    private final Header header;
    private final SampleBuffer sampleBuffer;
    private final long frameNumber;

    Mp3Frame(Header header, SampleBuffer sampleBuffer, long frameNumber) {
        this.header = header;
        this.sampleBuffer = sampleBuffer;
        this.frameNumber = frameNumber;
    }

    /**
     * Returns the backing sample array without copying. Only the first
     * {@link #getSampleCount()} entries are valid.
     */
    public short[] getSamples() {
        return sampleBuffer.getBuffer();
    }

    /** Returns a copy of the valid samples. */
    public short[] getSamplesCopy() {
        return sampleBuffer.getBufferCopy();
    }

    /** Returns the number of valid samples in this frame (all channels, interleaved). */
    public int getSampleCount() {
        return sampleBuffer.getBufferLength();
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

    /** Returns the duration of this frame in milliseconds. */
    public double getDurationMs() {
        return getSampleFrameCount() * 1000.0 / getSampleRate();
    }

    /** Returns the raw frame header. */
    public Header getHeader() {
        return header;
    }

    /** Returns the frame's PCM data as 16-bit little-endian bytes. */
    public byte[] toByteArrayLittleEndian() {
        return toPcm(ByteOrder.LITTLE_ENDIAN);
    }

    /** Returns the frame's PCM data as 16-bit big-endian bytes. */
    public byte[] toByteArrayBigEndian() {
        return toPcm(ByteOrder.BIG_ENDIAN);
    }

    private byte[] toPcm(ByteOrder order) {
        int count = getSampleCount();
        ByteBuffer out = ByteBuffer.allocate(count * 2).order(order);
        out.asShortBuffer().put(sampleBuffer.getBuffer(), 0, count);
        return out.array();
    }

    @Override
    public String toString() {
        return String.format("Mp3Frame{#%d, channels=%d, sampleRate=%d, samples=%d}",
                frameNumber, getChannels(), getSampleRate(), getSampleCount());
    }
}
