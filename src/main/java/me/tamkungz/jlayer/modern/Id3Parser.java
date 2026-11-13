package me.tamkungz.jlayer.modern;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Best-effort ID3v1 and ID3v2.2/2.3/2.4 reader. */
final class Id3Parser {

    private static final int MAX_TAG_SIZE = 32 * 1024 * 1024;
    private static final int ID3V1_SIZE = 128;
    private static final int PICTURE_TYPE_FRONT_COVER = 3;

    private String title;
    private String artist;
    private String album;
    private String albumArtist;
    private String year;
    private String track;
    private String genre;
    private Mp3Tags.CoverArt coverArt;
    private int coverArtType = -1;

    private Id3Parser() {}

    static Mp3Tags read(Path path) throws IOException {
        Id3Parser parser = new Id3Parser();
        try (SeekableByteChannel channel = Files.newByteChannel(path)) {
            parser.readV2(Channels.newInputStream(channel));
            parser.readV1(channel);
        }
        return parser.build();
    }

    static Mp3Tags read(InputStream in) throws IOException {
        Id3Parser parser = new Id3Parser();
        parser.readV2(in);
        return parser.build();
    }

    private Mp3Tags build() {
        return new Mp3Tags(title, artist, album, albumArtist, year, track, genre, coverArt);
    }

    private void readV2(InputStream in) throws IOException {
        byte[] header = in.readNBytes(10);
        if (header.length < 10 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
            return;
        }
        int major = header[3] & 0xFF;
        int flags = header[5] & 0xFF;
        if (major < 2 || major > 4 || !isSyncsafe(header, 6)) {
            return;
        }
        int size = syncsafe(header, 6);
        if (size <= 0 || size > MAX_TAG_SIZE) {
            return;
        }

        byte[] data = in.readNBytes(size);
        if ((flags & 0x80) != 0 && major < 4) {
            data = removeUnsynchronisation(data);
        }

        int pos = 0;
        if ((flags & 0x40) != 0 && major >= 3) {
            if (data.length < 4) {
                return;
            }
            int extended = major == 4 ? syncsafe(data, 0) : 4 + readInt(data, 0);
            if (extended < 4 || extended > data.length) {
                return;
            }
            pos = extended;
        }
        parseFrames(data, pos, major);
    }

    private void parseFrames(byte[] data, int pos, int major) {
        int idLength = major == 2 ? 3 : 4;
        int headerLength = major == 2 ? 6 : 10;

        while (pos + headerLength <= data.length && data[pos] != 0) {
            String id = new String(data, pos, idLength, StandardCharsets.ISO_8859_1);
            int frameSize;
            int frameFlags = 0;
            if (major == 2) {
                frameSize = (data[pos + 3] & 0xFF) << 16 | (data[pos + 4] & 0xFF) << 8 | (data[pos + 5] & 0xFF);
            } else {
                frameSize = major == 3 ? readInt(data, pos + 4) : syncsafe(data, pos + 4);
                frameFlags = (data[pos + 8] & 0xFF) << 8 | (data[pos + 9] & 0xFF);
            }
            int start = pos + headerLength;
            if (frameSize <= 0 || frameSize > data.length - start) {
                return;
            }
            int end = start + frameSize;
            pos = end;

            byte[] body = data;
            int offset = start;
            if (major == 3 && (frameFlags & 0x00C0) != 0) {
                continue;
            }
            if (major == 4) {
                if ((frameFlags & 0x000C) != 0) {
                    continue;
                }
                if ((frameFlags & 0x0001) != 0) {
                    offset += 4;
                }
                if (offset >= end) {
                    continue;
                }
                if ((frameFlags & 0x0002) != 0) {
                    body = removeUnsynchronisation(Arrays.copyOfRange(data, offset, end));
                    offset = 0;
                    end = body.length;
                }
            }
            handleFrame(id, body, offset, end);
        }
    }

    private void handleFrame(String id, byte[] d, int from, int to) {
        switch (id) {
            case "TIT2", "TT2" -> title = textOr(title, d, from, to);
            case "TPE1", "TP1" -> artist = textOr(artist, d, from, to);
            case "TALB", "TAL" -> album = textOr(album, d, from, to);
            case "TPE2", "TP2" -> albumArtist = textOr(albumArtist, d, from, to);
            case "TRCK", "TRK" -> track = textOr(track, d, from, to);
            case "TCON", "TCO" -> genre = textOr(genre, d, from, to);
            case "TDRC", "TYER", "TYE" -> {
                String value = text(d, from, to);
                if (value != null && year == null) {
                    year = value.length() > 4 ? value.substring(0, 4) : value;
                }
            }
            case "APIC", "PIC" -> picture(d, from, to, id.equals("PIC"));
            default -> { }
        }
    }

    private static String textOr(String current, byte[] d, int from, int to) {
        return current != null ? current : text(d, from, to);
    }

    private static String text(byte[] d, int from, int to) {
        if (to - from < 2) {
            return null;
        }
        String value = decode(d, from + 1, to, d[from] & 0xFF);
        return value.isEmpty() ? null : value;
    }

    private void picture(byte[] d, int from, int to, boolean v22) {
        if (to - from < 4) {
            return;
        }
        int encoding = d[from] & 0xFF;
        int pos = from + 1;
        String mime;
        if (v22) {
            String format = new String(d, pos, 3, StandardCharsets.ISO_8859_1).toLowerCase();
            mime = format.equals("jpg") ? "image/jpeg" : "image/" + format;
            pos += 3;
        } else {
            int mimeEnd = indexOfTerminator(d, pos, to, 0);
            if (mimeEnd < 0) {
                return;
            }
            mime = new String(d, pos, mimeEnd - pos, StandardCharsets.ISO_8859_1);
            pos = mimeEnd + 1;
            if (mime.isEmpty()) {
                mime = "image/";
            }
        }
        if (pos >= to) {
            return;
        }
        int type = d[pos++] & 0xFF;
        int descriptionEnd = indexOfTerminator(d, pos, to, encoding);
        if (descriptionEnd < 0) {
            return;
        }
        pos = descriptionEnd + terminatorLength(encoding);
        if (pos >= to) {
            return;
        }
        boolean better = coverArt == null
                || (type == PICTURE_TYPE_FRONT_COVER && coverArtType != PICTURE_TYPE_FRONT_COVER);
        if (better) {
            coverArt = new Mp3Tags.CoverArt(mime, Arrays.copyOfRange(d, pos, to));
            coverArtType = type;
        }
    }

    private void readV1(SeekableByteChannel channel) throws IOException {
        long size = channel.size();
        if (size < ID3V1_SIZE) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(ID3V1_SIZE);
        channel.position(size - ID3V1_SIZE);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                return;
            }
        }
        byte[] b = buffer.array();
        if (b[0] != 'T' || b[1] != 'A' || b[2] != 'G') {
            return;
        }
        title = title != null ? title : latin1(b, 3, 33);
        artist = artist != null ? artist : latin1(b, 33, 63);
        album = album != null ? album : latin1(b, 63, 93);
        year = year != null ? year : latin1(b, 93, 97);
        if (track == null && b[125] == 0 && b[126] != 0) {
            track = Integer.toString(b[126] & 0xFF);
        }
    }

    private static String latin1(byte[] b, int from, int to) {
        int end = from;
        while (end < to && b[end] != 0) {
            end++;
        }
        String value = new String(b, from, end - from, StandardCharsets.ISO_8859_1).strip();
        return value.isEmpty() ? null : value;
    }

    private static String decode(byte[] d, int from, int to, int encoding) {
        Charset charset = switch (encoding) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
        String value = new String(d, from, to - from, charset);
        int terminator = value.indexOf('\0');
        return (terminator >= 0 ? value.substring(0, terminator) : value).strip();
    }

    private static int terminatorLength(int encoding) {
        return (encoding == 1 || encoding == 2) ? 2 : 1;
    }

    private static int indexOfTerminator(byte[] d, int from, int to, int encoding) {
        if (terminatorLength(encoding) == 1) {
            for (int i = from; i < to; i++) {
                if (d[i] == 0) {
                    return i;
                }
            }
            return -1;
        }
        for (int i = from; i + 1 < to; i += 2) {
            if (d[i] == 0 && d[i + 1] == 0) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSyncsafe(byte[] b, int offset) {
        return ((b[offset] | b[offset + 1] | b[offset + 2] | b[offset + 3]) & 0x80) == 0;
    }

    private static int syncsafe(byte[] b, int offset) {
        return (b[offset] & 0x7F) << 21 | (b[offset + 1] & 0x7F) << 14
                | (b[offset + 2] & 0x7F) << 7 | (b[offset + 3] & 0x7F);
    }

    private static int readInt(byte[] b, int offset) {
        return (b[offset] & 0xFF) << 24 | (b[offset + 1] & 0xFF) << 16
                | (b[offset + 2] & 0xFF) << 8 | (b[offset + 3] & 0xFF);
    }

    private static byte[] removeUnsynchronisation(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        for (int i = 0; i < in.length; i++) {
            out.write(in[i]);
            if ((in[i] & 0xFF) == 0xFF && i + 1 < in.length && in[i + 1] == 0) {
                i++;
            }
        }
        return out.toByteArray();
    }
}
