package me.tamkungz.jlayer.modern.player;

import me.tamkungz.jlayer.modern.Mp3Exception;

/**
 * Unchecked exception thrown by {@link AdvancedPlayer}.
 */
public class AdvancedPlayerException extends Mp3Exception {
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
