package javazoom.jl.modern.advanced;

/**
 * Unchecked exception thrown by {@link AdvancedPlayer}.
 */
public class AdvancedPlayerException extends RuntimeException {
    public AdvancedPlayerException(String message) {
        super(message);
    }

    public AdvancedPlayerException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdvancedPlayerException(Throwable cause) {
        super(cause);
    }
}