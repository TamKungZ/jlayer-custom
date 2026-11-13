package me.tamkungz.jlayer.modern;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;

/**
 * Immutable metadata about an MP3 stream, taken from its first frame header.
 * <p>
 * Obtained from {@link Mp3Decoder#getInfo()}, or without a decoder through
 * {@link #read(Path)} and {@link #probe(InputStream, long)}.
 * </p>
 *
 * @since 1.0
 */
public final class Mp3Info {

    private final int sampleRate;
    private final int channels;
    private final int bitrate;
    private final int instantBitrate;
    private final float durationMs;
    private final long frameCount;
    private final boolean vbr;
    private final String layer;
    private final String version;

    /**
     * Constructs an Mp3Info from the first frame header and total stream size.
     *
     * @param header the first frame header
     * @param streamSizeBytes total stream size in bytes, or a value &lt;= 0 if unknown
     */
    public Mp3Info(Header header, long streamSizeBytes) {
        Objects.requireNonNull(header, "header must not be null");
        this.sampleRate = header.frequency();
        this.channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
        this.bitrate = header.bitrate();
        this.instantBitrate = header.bitrateInstant();
        this.vbr = header.vbr();
        this.layer = header.layerString();
        this.version = header.versionString();

        if (streamSizeBytes > 0) {
            int size = (int) Math.min(streamSizeBytes, Integer.MAX_VALUE);
            this.frameCount = header.maxNumberOfFrames(size);
            this.durationMs = header.totalMs(size);
        } else {
            this.frameCount = -1;
            this.durationMs = -1;
        }
    }

    /**
     * Reads the info of an MP3 file without creating a decoder.
     *
     * @throws IOException if the file cannot be read or contains no MPEG frame
     */
    public static Mp3Info read(Path path) throws IOException {
        long size = Files.size(path);
        try (InputStream in = Files.newInputStream(path)) {
            return probe(in, size);
        }
    }

    /**
     * Reads the info from the first frame of {@code in}. The stream is consumed
     * (an unknown amount, at least the first frame and any ID3v2 tag) and is not
     * closed.
     *
     * @param in the stream to probe
     * @param streamSizeBytes total stream size in bytes, or a value &lt;= 0 if unknown
     * @throws IOException if the stream cannot be read or contains no MPEG frame
     */
    public static Mp3Info probe(InputStream in, long streamSizeBytes) throws IOException {
        Bitstream bitstream = new Bitstream(new FilterInputStream(in) {
            @Override
            public void close() {
                // Bitstream.close() must not close the caller's stream.
            }
        });
        try {
            Header header = bitstream.readFrame();
            if (header == null) {
                throw new IOException("No MPEG frames found in stream");
            }
            return new Mp3Info(header, streamSizeBytes);
        } catch (BitstreamException e) {
            throw new IOException("Failed to read stream info", e);
        } finally {
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
                // nothing to release: the wrapped stream stays open
            }
        }
    }

    public int getSampleRate() { return sampleRate; }
    public int getChannels() { return channels; }

    /** Average bitrate in bits per second. */
    public int getBitrate() { return bitrate; }

    /** Bitrate of the first frame in bits per second. */
    public int getInstantBitrate() { return instantBitrate; }

    /** Estimated duration in milliseconds, or a negative value if unknown. */
    public float getDurationMs() { return durationMs; }

    /** Estimated duration, empty if the stream size was unknown. */
    public Optional<Duration> getDuration() {
        return durationMs < 0
                ? Optional.empty()
                : Optional.of(Duration.ofNanos(Math.round(durationMs * 1_000_000.0)));
    }

    /** Estimated number of frames, or a negative value if unknown. */
    public long getFrameCount() { return frameCount; }
    public boolean isVbr() { return vbr; }
    public String getLayer() { return layer; }
    public String getVersion() { return version; }

    @Override
    public String toString() {
        return String.format(
            "Mp3Info{sampleRate=%d Hz, channels=%d, bitrate=%d bps, vbr=%s, layer=%s, duration=%.2f s}",
            sampleRate, channels, bitrate, vbr, layer, durationMs / 1000);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mp3Info that)) return false;
        return sampleRate == that.sampleRate
                && channels == that.channels
                && bitrate == that.bitrate
                && instantBitrate == that.instantBitrate
                && Float.compare(that.durationMs, durationMs) == 0
                && frameCount == that.frameCount
                && vbr == that.vbr
                && layer.equals(that.layer)
                && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sampleRate, channels, bitrate, instantBitrate,
                            durationMs, frameCount, vbr, layer, version);
    }
}
