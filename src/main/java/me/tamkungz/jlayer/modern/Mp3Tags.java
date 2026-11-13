package me.tamkungz.jlayer.modern;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable ID3 tag data of an MP3 file: the common text fields and the front
 * cover picture.
 * <p>
 * ID3v2.2, v2.3 and v2.4 tags are supported; for files, the ID3v1 tag at the end
 * is used as a fallback for fields the ID3v2 tag does not provide. Tag parsing is
 * best-effort: malformed frames are skipped instead of failing the whole read.
 * </p>
 *
 * @since 1.2.0
 */
public final class Mp3Tags {

    /** A tag set with no data. */
    public static final Mp3Tags EMPTY = new Mp3Tags(null, null, null, null, null, null, null, null);

    /**
     * An embedded picture.
     *
     * @param mimeType the MIME type, e.g. {@code image/jpeg}
     * @param data the encoded image bytes
     */
    public record CoverArt(String mimeType, byte[] data) {
        public CoverArt {
            Objects.requireNonNull(mimeType, "mimeType");
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }

    private final String title;
    private final String artist;
    private final String album;
    private final String albumArtist;
    private final String year;
    private final String track;
    private final String genre;
    private final CoverArt coverArt;

    Mp3Tags(String title, String artist, String album, String albumArtist,
            String year, String track, String genre, CoverArt coverArt) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtist = albumArtist;
        this.year = year;
        this.track = track;
        this.genre = genre;
        this.coverArt = coverArt;
    }

    /**
     * Reads the tags of an MP3 file (ID3v2 at the start, ID3v1 at the end).
     *
     * @return the tags, {@link #EMPTY} if the file has none
     * @throws IOException if the file cannot be read
     */
    public static Mp3Tags read(Path path) throws IOException {
        return Id3Parser.read(path);
    }

    /**
     * Reads the ID3v2 tag at the start of {@code in}. The stream is not closed.
     * ID3v1 tags are not available because they sit at the end of the file.
     *
     * @return the tags, {@link #EMPTY} if the stream does not start with an ID3v2 tag
     * @throws IOException if the stream cannot be read
     */
    public static Mp3Tags read(InputStream in) throws IOException {
        return Id3Parser.read(in);
    }

    public Optional<String> getTitle() { return Optional.ofNullable(title); }
    public Optional<String> getArtist() { return Optional.ofNullable(artist); }
    public Optional<String> getAlbum() { return Optional.ofNullable(album); }
    public Optional<String> getAlbumArtist() { return Optional.ofNullable(albumArtist); }

    /** The recording year, e.g. {@code 2024}. */
    public Optional<String> getYear() { return Optional.ofNullable(year); }

    /** The track position as written in the tag, e.g. {@code 3} or {@code 3/12}. */
    public Optional<String> getTrack() { return Optional.ofNullable(track); }

    /** The genre as written in the tag. */
    public Optional<String> getGenre() { return Optional.ofNullable(genre); }

    /** The front cover if present, otherwise the first embedded picture. */
    public Optional<CoverArt> getCoverArt() { return Optional.ofNullable(coverArt); }

    public boolean isEmpty() {
        return title == null && artist == null && album == null && albumArtist == null
                && year == null && track == null && genre == null && coverArt == null;
    }

    @Override
    public String toString() {
        return "Mp3Tags{title=" + title + ", artist=" + artist + ", album=" + album
                + ", year=" + year + ", track=" + track + ", genre=" + genre
                + ", coverArt=" + (coverArt == null ? "none" : coverArt.mimeType()) + "}";
    }
}
