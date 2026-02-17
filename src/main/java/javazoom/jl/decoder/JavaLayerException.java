package javazoom.jl.decoder;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Base exception type for all API-level errors thrown by JavaLayer.
 *
 * <p>
 * Provides legacy exception delegation while supporting
 * modern Java exception chaining.
 * </p>
 *
 * <h2>Compatibility</h2>
 * <ul>
 *   <li>Original constructors preserved</li>
 *   <li>{@link #getException()} preserved</li>
 *   <li>Legacy stack trace behavior preserved</li>
 * </ul>
 */
public class JavaLayerException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Legacy contained exception (pre-1.4 style).
     */
    private Throwable exception;

    public JavaLayerException() {
        super();
    }

    public JavaLayerException(String msg) {
        super(msg);
    }

    public JavaLayerException(String msg, Throwable t) {
        super(msg, t);
        this.exception = t;
    }

    /**
     * Creates an exception with a cause.
     *
     * @param cause underlying cause
     */
    public JavaLayerException(Throwable cause) {
        super(cause);
        this.exception = cause;
    }

    /**
     * Returns contained exception (legacy accessor).
     *
     * @return underlying exception or null
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * Returns true if this exception has a cause.
     *
     * @return true if cause exists
     */
    public boolean hasCause() {
        return getCause() != null;
    }

    /**
     * Returns the root cause of this exception.
     *
     * @return deepest cause or this if none
     */
    public Throwable getRootCause() {
        Throwable root = this;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /**
     * Returns message including cause message (if present).
     *
     * @return extended message
     */
    public String getMessageWithCause() {
        if (!hasCause()) {
            return getMessage();
        }
        return getMessage() + " (Caused by: " + getCause().getMessage() + ")";
    }

    /**
     * Wraps any throwable into a JavaLayerException.
     *
     * @param t throwable to wrap
     * @return JavaLayerException instance
     */
    public static JavaLayerException wrap(Throwable t) {
        if (t instanceof JavaLayerException jle) {
            return jle;
        }
        return new JavaLayerException(t);
    }

    @Override
    public void printStackTrace() {
        printStackTrace(System.err);
    }

    @Override
    public void printStackTrace(PrintStream ps) {
        if (exception == null) {
            super.printStackTrace(ps);
        } else {
            exception.printStackTrace(ps);
        }
    }

    @Override
    public void printStackTrace(PrintWriter pw) {
        if (exception == null) {
            super.printStackTrace(pw);
        } else {
            exception.printStackTrace(pw);
        }
    }

    /**
     * Returns string representation including cause information.
     */
    @Override
    public String toString() {
        if (!hasCause()) {
            return super.toString();
        }
        return super.toString() + " [cause=" + getCause().toString() + "]";
    }
}
