package javazoom.jl.decoder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * <p>
 * {@code JavaLayerHook} allows customization of how JavaLayer loads
 * internal or external resources.
 * </p>
 *
 * <p>
 * This abstraction enables integration with:
 * </p>
 * <ul>
 *     <li>Custom class loaders</li>
 *     <li>Network-based resources</li>
 *     <li>Encrypted resource stores</li>
 *     <li>Embedded runtime environments</li>
 * </ul>
 *
 * <h2>Backward Compatibility</h2>
 * <p>
 * The original {@link #getResourceAsStream(String)} method is preserved.
 * Additional default methods are safe extensions.
 * </p>
 */
public interface JavaLayerHook {

    /**
     * Retrieves a named resource as an {@link InputStream}.
     *
     * @param name resource name (must not be null)
     * @return input stream for the resource, or {@code null} if not found
     */
    InputStream getResourceAsStream(String name);

    /**
     * Retrieves a resource and throws an exception if not found.
     *
     * @param name resource name
     * @return input stream (never null)
     * @throws IOException if resource cannot be found
     */
    default InputStream requireResource(String name) throws IOException {
        Objects.requireNonNull(name, "Resource name cannot be null");

        InputStream in = getResourceAsStream(name);
        if (in == null) {
            throw new IOException("Resource not found: " + name);
        }
        return in;
    }

    /**
     * Wraps another hook and provides fallback behavior.
     *
     * @param fallback fallback hook
     * @return composed hook
     */
    default JavaLayerHook orElse(JavaLayerHook fallback) {
        Objects.requireNonNull(fallback);

        return name -> {
            InputStream in = this.getResourceAsStream(name);
            if (in != null) {
                return in;
            }
            return fallback.getResourceAsStream(name);
        };
    }

    /**
     * Creates a hook backed by a {@link ClassLoader}.
     *
     * @param classLoader class loader to use
     * @return hook implementation
     */
    static JavaLayerHook fromClassLoader(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader);

        return name -> classLoader.getResourceAsStream(name);
    }

    /**
     * Creates a hook backed by the current thread context class loader.
     *
     * @return hook implementation
     */
    static JavaLayerHook contextClassLoader() {
        return fromClassLoader(Thread.currentThread().getContextClassLoader());
    }
}
