package me.tamkungz.jlayer.modern;

import javazoom.jl.decoder.JavaLayerException;

/**
 * Unchecked exception thrown by the modern MP3 API.
 * Wraps checked {@link JavaLayerException} and other IO exceptions.
 * The player exceptions in {@code me.tamkungz.jlayer.modern.player} extend this class.
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
