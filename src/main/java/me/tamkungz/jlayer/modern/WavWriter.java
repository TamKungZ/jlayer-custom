package me.tamkungz.jlayer.modern;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Builds the canonical 44-byte header of a 16-bit PCM WAV file. */
final class WavWriter {

    static final int HEADER_SIZE = 44;

    private static final long MAX_DATA_LENGTH = 0xFFFFFFFFL - (HEADER_SIZE - 8);

    private WavWriter() {}

    static byte[] header(int sampleRate, int channels, long dataLength) {
        if (dataLength > MAX_DATA_LENGTH) {
            throw new Mp3Exception("PCM data is too large for a WAV file: " + dataLength + " bytes");
        }
        int blockAlign = channels * 2;
        ByteBuffer b = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[] {'R', 'I', 'F', 'F'});
        b.putInt((int) (dataLength + HEADER_SIZE - 8));
        b.put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) channels);
        b.putInt(sampleRate);
        b.putInt(sampleRate * blockAlign);
        b.putShort((short) blockAlign);
        b.putShort((short) 16);
        b.put(new byte[] {'d', 'a', 't', 'a'});
        b.putInt((int) dataLength);
        return b.array();
    }
}
