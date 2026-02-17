package javazoom.jl.modern;

/**
 * Unchecked exception thrown by {@link Mp3Player}.
 */
public class Mp3PlayerException extends RuntimeException {
    public Mp3PlayerException(String message) {
        super(message);
    }

    public Mp3PlayerException(String message, Throwable cause) {
        super(message, cause);
    }

    public Mp3PlayerException(Throwable cause) {
        super(cause);
    }
}