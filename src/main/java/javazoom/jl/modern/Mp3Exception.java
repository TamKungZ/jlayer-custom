package javazoom.jl.modern;

import javazoom.jl.decoder.JavaLayerException;

/**
 * Unchecked exception thrown by the modern MP3 decoder wrapper.
 * Wraps checked {@link JavaLayerException} and other IO exceptions.
 *
 * @since 1.0
 */
public class Mp3Exception extends RuntimeException {

    public Mp3Exception(String message) {
        super(message);
    }

    public Mp3Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public Mp3Exception(Throwable cause) {
        super(cause);
    }
}