package me.tamkungz.jlayer.modern;

import java.util.Iterator;
import java.util.NoSuchElementException;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.Obuffer;
import javazoom.jl.decoder.SampleBuffer;

/**
 * Single-pass iterator over the frames of a {@link Bitstream}. It always keeps the
 * next header read ahead so {@link #hasNext()} is exact.
 */
final class Mp3FrameIterator implements Iterator<Mp3Frame> {

    private final Bitstream bitstream;
    private final Decoder decoder;
    private Header nextHeader;
    private boolean endOfStream;
    private long frameNumber;

    Mp3FrameIterator(Bitstream bitstream, Decoder decoder) {
        this.bitstream = bitstream;
        this.decoder = decoder;
        advance();
    }

    @Override
    public boolean hasNext() {
        return !endOfStream;
    }

    @Override
    public Mp3Frame next() {
        if (endOfStream) {
            throw new NoSuchElementException();
        }

        Header header = nextHeader;
        try {
            Obuffer decoded = decoder.decodeFrame(header, bitstream);
            bitstream.closeFrame();
            if (!(decoded instanceof SampleBuffer samples)) {
                throw new Mp3Exception("The output buffer must be a SampleBuffer");
            }
            return emit(header, samples);
        } catch (DecoderException e) {
            throw new Mp3Exception("Decoding error at frame " + frameNumber, e);
        }
    }

    private Mp3Frame emit(Header header, SampleBuffer samples) {
        // The decoder reuses its output buffer, so each frame keeps a snapshot.
        Mp3Frame frame = new Mp3Frame(header, samples.copy(), frameNumber++);
        advance();
        return frame;
    }

    /** Skips the next frame without decoding it. Returns false at end of stream. */
    boolean skip() {
        if (endOfStream) {
            return false;
        }
        bitstream.closeFrame();
        frameNumber++;
        advance();
        return true;
    }

    private void advance() {
        try {
            nextHeader = bitstream.readFrame();
        } catch (BitstreamException e) {
            throw new Mp3Exception("Failed to read frame " + frameNumber, e);
        }
        endOfStream = nextHeader == null;
    }
}
