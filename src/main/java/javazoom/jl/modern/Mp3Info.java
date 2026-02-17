package javazoom.jl.modern;

import java.util.Objects;

import javazoom.jl.decoder.Header;

/**
 * Immutable metadata about an MP3 stream.
 * <p>
 * Obtained from {@link Mp3Decoder#getInfo()}.
 * </p>
 *
 * @since 1.0
 */
public final class Mp3Info {

    private final int sampleRate;
    private final int channels;
    private final int bitrate;          // average bitrate (bps)
    private final int instantBitrate;    // bitrate of first frame (bps)
    private final float durationMs;      // estimated total duration (ms)
    private final long frameCount;       // estimated number of frames
    private final boolean vbr;
    private final String layer;
    private final String version;

    /**
     * Constructs an Mp3Info from the first frame header and total stream size.
     */
    public Mp3Info(Header header, long streamSizeBytes) {
        this.sampleRate = header.frequency();
        this.channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
        this.bitrate = header.bitrate();               // average if VBR
        this.instantBitrate = header.bitrateInstant(); // first frame bitrate
        this.vbr = header.vbr();
        this.layer = header.layerString();
        this.version = header.versionString();

        if (streamSizeBytes > 0) {
            this.frameCount = header.maxNumberOfFrames((int) streamSizeBytes);
            this.durationMs = header.totalMs((int) streamSizeBytes);
        } else {
            this.frameCount = -1;
            this.durationMs = -1;
        }
    }

    public int getSampleRate() { return sampleRate; }
    public int getChannels() { return channels; }
    public int getBitrate() { return bitrate; }
    public int getInstantBitrate() { return instantBitrate; }
    public float getDurationMs() { return durationMs; }
    public long getFrameCount() { return frameCount; }
    public boolean isVbr() { return vbr; }
    public String getLayer() { return layer; }
    public String getVersion() { return version; }

    /**
     * Returns a human-readable summary of the MP3 info.
      * Example: "Mp3Info{sampleRate=44100 Hz, channels=2, bitrate=128000 bps, vbr=false, layer=Layer III, duration=215.32 s}"
      * @return summary string
     */
    @Override
    public String toString() {
        return String.format(
            "Mp3Info{sampleRate=%d Hz, channels=%d, bitrate=%d bps, vbr=%s, layer=%s, duration=%.2f s}",
            sampleRate, channels, bitrate, vbr, layer, durationMs / 1000);
    }

    /**
     * Two Mp3Info objects are equal if all their fields are equal.
      * @param o other object
      * @return true if equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mp3Info)) return false;
        Mp3Info that = (Mp3Info) o;
        return sampleRate == that.sampleRate &&
               channels == that.channels &&
               bitrate == that.bitrate &&
               instantBitrate == that.instantBitrate &&
               Float.compare(that.durationMs, durationMs) == 0 &&
               frameCount == that.frameCount &&
               vbr == that.vbr &&
               layer.equals(that.layer) &&
               version.equals(that.version);
    }

    /**
     * Computes hash code based on all fields.
      * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(sampleRate, channels, bitrate, instantBitrate,
                            durationMs, frameCount, vbr, layer, version);
    }
}