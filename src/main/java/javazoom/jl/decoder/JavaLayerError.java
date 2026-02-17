package javazoom.jl.decoder;

/**
 * <p>
 * Serious internal error thrown by JavaLayer.
 * </p>
 *
 * <p>
 * This class extends {@link Error}, meaning it represents
 * unrecoverable conditions similar to {@link VirtualMachineError}.
 * It is typically used for API misuse or fatal decoder failures.
 * </p>
 *
 * <h2>Compatibility</h2>
 * <ul>
 *   <li>Original no-arg constructor preserved</li>
 *   <li>Additional constructors added (safe)</li>
 * </ul>
 */
public class JavaLayerError extends Error {

    private static final long serialVersionUID = 1L;

    /**
     * Optional error code.
     */
    private final int errorCode;

    /**
     * Creates a JavaLayerError with no detail message.
     */
    public JavaLayerError() {
        this(null, null, 0);
    }

    /**
     * Creates a JavaLayerError with message.
     *
     * @param message error message
     */
    public JavaLayerError(String message) {
        this(message, null, 0);
    }

    /**
     * Creates a JavaLayerError with message and cause.
     *
     * @param message error message
     * @param cause underlying cause
     */
    public JavaLayerError(String message, Throwable cause) {
        this(message, cause, 0);
    }

    /**
     * Creates a JavaLayerError with message, cause and error code.
     *
     * @param message error message
     * @param cause underlying cause
     * @param errorCode application-specific error code
     */
    public JavaLayerError(String message, Throwable cause, int errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns associated error code.
     *
     * @return error code (0 if not set)
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Returns true if an error code is set.
     *
     * @return true if error code != 0
     */
    public boolean hasErrorCode() {
        return errorCode != 0;
    }

    @Override
    public String toString() {
        if (!hasErrorCode()) {
            return super.toString();
        }
        return super.toString() + " [code=0x" +
                Integer.toHexString(errorCode) + "]";
    }
}
