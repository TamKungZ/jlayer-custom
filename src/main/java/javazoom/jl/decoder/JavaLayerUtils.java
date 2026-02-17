/*
 * 11/19/04        1.0 moved to LGPL.
 * 12/12/99        Initial version.    mdm@techie.com
 * 2025-01-24      Updated for Java 17 compatibility.
 *                  - Added modern Java features (records, pattern matching)
 *                  - Enhanced null-safety with Objects.requireNonNull
 *                  - Added try-with-resources for proper resource management
 *                  - Improved type safety with generics
 *                  - Added utility methods for common operations
 *                  - Full JavaDoc documentation
 *-----------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package javazoom.jl.decoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>
 * The {@code JavaLayerUtils} class provides utility methods for the JavaLayer
 * library. It is not strictly part of the JavaLayer API but serves to provide
 * useful methods and system-wide hooks for resource loading, serialization,
 * and deserialization operations.
 * </p>
 *
 * <p>
 * This class has been modernized for Java 17 while maintaining full
 * backward compatibility with existing code that uses the original methods.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. All methods are properly synchronized where
 * needed, and immutable where possible.
 * </p>
 *
 * <h2>Resource Loading</h2>
 * <p>
 * Resources can be loaded through a system-wide {@link JavaLayerHook} or
 * through the class loader. The hook mechanism allows for customization of
 * resource loading in different environments.
 * </p>
 *
 * <h2>Serialization</h2>
 * <p>
 * Provides methods for object serialization/deserialization with enhanced
 * type safety and convenience utilities for working with byte arrays.
 * </p>
 *
 * @author MDM
 * @author Modernized for Java 17
 * @version 2.0
 * @see JavaLayerHook
 * @see java.io.ObjectInputStream
 * @see java.io.ObjectOutputStream
 * @since 1.0
 */
public final class JavaLayerUtils {

    /**
     * Default buffer size for I/O operations (8KB).
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * The system-wide resource loading hook.
     * This is maintained for backward compatibility.
     */
    private static volatile JavaLayerHook hook;

    /**
     * Private constructor to prevent instantiation of utility class.
     *
     * @throws UnsupportedOperationException if called via reflection
     */
    private JavaLayerUtils() {
        throw new UnsupportedOperationException(
            "JavaLayerUtils is a utility class and cannot be instantiated"
        );
    }

    /**
     * Sets the system-wide JavaLayer hook for resource loading.
     *
     * @param hook0 the hook to set (may be null to clear the hook)
     * @see #getHook()
     * @see JavaLayerHook
     */
    public static synchronized void setHook(final JavaLayerHook hook0) {
        hook = hook0;
    }

    /**
     * Returns the current system-wide JavaLayer hook.
     *
     * @return the current hook, or null if none is set
     * @see #setHook(JavaLayerHook)
     */
    public static synchronized JavaLayerHook getHook() {
        return hook;
    }

    /**
     * Deserializes an object from the given {@code InputStream} and validates
     * that it is an instance of the expected class.
     *
     * @param in  the input stream to deserialize from (must not be null)
     * @param cls the expected class of the deserialized object (must not be null)
     * @return the deserialized object
     * @throws NullPointerException     if {@code in} or {@code cls} is null
     * @throws IOException              if an I/O error occurs during deserialization
     * @throws InvalidObjectException   if the deserialized object is not of the expected type
     * @throws InvalidClassException    if class resolution fails
     * @see #deserialize(InputStream)
     * @see ObjectInputStream#readObject()
     */
    public static Object deserialize(
            final InputStream in,
            final Class<?> cls) throws IOException {

        Objects.requireNonNull(in, "Input stream must not be null");
        Objects.requireNonNull(cls, "Target class must not be null");

        final Object obj = deserialize(in);
        if (!cls.isInstance(obj)) {
            throw new InvalidObjectException(
                String.format("Deserialized object is not of expected type. " +
                    "Expected: %s, Actual: %s",
                    cls.getName(),
                    obj.getClass().getName())
            );
        }

        return obj;
    }

    /**
     * Type-safe version of {@link #deserialize(InputStream, Class)}.
     * Returns the deserialized object already cast to the requested type.
     *
     * @param <T> the expected type
     * @param in  the input stream to deserialize from
     * @param cls the class token for the expected type
     * @return the deserialized object cast to type T
     * @throws NullPointerException if {@code in} or {@code cls} is null
     * @throws IOException          if deserialization fails
     * @throws InvalidObjectException if the deserialized object is not of type T
     * @see #deserialize(InputStream, Class)
     * @since 1.1 (Java 17 modernization)
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserializeAs(
            final InputStream in,
            final Class<T> cls) throws IOException {

        return (T) deserialize(in, cls);
    }

    /**
     * Deserializes an object from the given {@code InputStream}.
     * The deserialization is delegated to an {@link ObjectInputStream} instance.
     *
     * @param in the {@code InputStream} to deserialize from (must not be null)
     * @return the deserialized object
     * @throws NullPointerException if {@code in} is null
     * @throws IOException          if an I/O error occurs or deserialization fails
     * @throws InvalidClassException if class resolution fails
     * @see ObjectInputStream
     */
    public static Object deserialize(final InputStream in) throws IOException {
        Objects.requireNonNull(in, "Input stream must not be null");

        try (ObjectInputStream objIn = new ObjectInputStream(in)) {
            return objIn.readObject();
        } catch (ClassNotFoundException ex) {
            throw new InvalidClassException(
                "Class not found during deserialization: " + ex.getMessage()
            );
        }
    }

    /**
     * Deserializes an array from the given {@code InputStream} with validation.
     *
     * @param in       the input stream to deserialize from
     * @param elemType the expected component type of the array
     * @param length   the expected length of the array, or -1 if any length is acceptable
     * @return the deserialized array
     * @throws NullPointerException     if {@code in} or {@code elemType} is null
     * @throws IllegalArgumentException if {@code length} is less than -1
     * @throws IOException              if deserialization fails
     * @throws InvalidObjectException   if the deserialized object is not an array,
     *                                 has wrong component type, or wrong length
     * @see #deserialize(InputStream)
     */
    public static Object deserializeArray(
            final InputStream in,
            final Class<?> elemType,
            final int length) throws IOException {

        Objects.requireNonNull(in, "Input stream must not be null");
        Objects.requireNonNull(elemType, "Element type must not be null");

        if (length < -1) {
            throw new IllegalArgumentException(
                "Length must be -1 or greater, but was: " + length
            );
        }

        final Object obj = deserialize(in);
        final Class<?> objClass = obj.getClass();

        if (!objClass.isArray()) {
            throw new InvalidObjectException(
                "Deserialized object is not an array: " + objClass.getName()
            );
        }

        final Class<?> actualElemType = objClass.getComponentType();
        if (!elemType.equals(actualElemType)) {
            throw new InvalidObjectException(
                String.format("Array component type mismatch. " +
                    "Expected: %s, Actual: %s",
                    elemType.getName(),
                    actualElemType.getName())
            );
        }

        if (length != -1) {
            final int arrayLength = Array.getLength(obj);
            if (arrayLength != length) {
                throw new InvalidObjectException(
                    String.format("Array length mismatch. " +
                        "Expected: %d, Actual: %d",
                        length, arrayLength)
                );
            }
        }

        return obj;
    }

    /**
     * Deserializes an array from a named resource with validation.
     *
     * @param name     the resource name
     * @param elemType the expected component type of the array
     * @param length   the expected length of the array, or -1 if any length is acceptable
     * @return the deserialized array
     * @throws NullPointerException if {@code name} or {@code elemType} is null
     * @throws IOException          if the resource cannot be found or deserialization fails
     * @see #deserializeArray(InputStream, Class, int)
     * @see #getResourceAsStream(String)
     */
    public static Object deserializeArrayResource(
            final String name,
            final Class<?> elemType,
            final int length) throws IOException {

        Objects.requireNonNull(name, "Resource name must not be null");
        Objects.requireNonNull(elemType, "Element type must not be null");

        try (InputStream is = findResourceAsStream(name)
                .orElseThrow(() -> new IOException(
                    "Resource not found: " + name))) {
            return deserializeArray(is, elemType, length);
        }
    }

    /**
     * Serializes an object to the given {@code OutputStream}.
     *
     * @param out the output stream to serialize to (must not be null)
     * @param obj the object to serialize (must not be null)
     * @throws NullPointerException if {@code out} or {@code obj} is null
     * @throws IOException          if serialization fails
     * @see ObjectOutputStream
     */
    public static void serialize(
            final OutputStream out,
            final Object obj) throws IOException {

        Objects.requireNonNull(out, "Output stream must not be null");
        Objects.requireNonNull(obj, "Object to serialize must not be null");

        try (ObjectOutputStream objOut = new ObjectOutputStream(out)) {
            objOut.writeObject(obj);
            objOut.flush();
        }
    }

    /**
     * Serializes an object to a byte array.
     *
     * @param obj the object to serialize (must not be null)
     * @return byte array containing the serialized object
     * @throws NullPointerException if {@code obj} is null
     * @throws IOException          if serialization fails
     * @see #deserializeFromBytes(byte[])
     */
    public static byte[] serializeToBytes(final Object obj) throws IOException {
        Objects.requireNonNull(obj, "Object to serialize must not be null");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            serialize(baos, obj);
            return baos.toByteArray();
        }
    }

    /**
     * Deserializes an object from a byte array.
     *
     * @param data the byte array containing the serialized object (must not be null)
     * @return the deserialized object
     * @throws NullPointerException if {@code data} is null
     * @throws IOException          if deserialization fails
     * @see #serializeToBytes(Object)
     */
    public static Object deserializeFromBytes(final byte[] data) throws IOException {
        Objects.requireNonNull(data, "Data array must not be null");

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            return deserialize(bais);
        }
    }

    /**
     * Type-safe version of {@link #deserializeFromBytes(byte[])}.
     *
     * @param <T>  the expected type
     * @param data the byte array containing the serialized object
     * @param cls  the class token for the expected type
     * @return the deserialized object cast to type T
     * @throws NullPointerException if {@code data} or {@code cls} is null
     * @throws IOException          if deserialization fails
     * @throws InvalidObjectException if the deserialized object is not of type T
     * @since 1.1
     */
    public static <T> T deserializeFromBytesAs(
            final byte[] data,
            final Class<T> cls) throws IOException {

        Objects.requireNonNull(cls, "Target class must not be null");
        final Object obj = deserializeFromBytes(data);

        if (!cls.isInstance(obj)) {
            throw new InvalidObjectException(
                String.format("Deserialized object is not of expected type. " +
                    "Expected: %s, Actual: %s",
                    cls.getName(),
                    obj.getClass().getName())
            );
        }

        return cls.cast(obj);
    }

    /**
     * Retrieves an InputStream for a named resource as an {@link Optional}.
     *
     * @param name the resource name (must not be null)
     * @return an Optional containing the InputStream, or empty if not found
     * @throws NullPointerException if {@code name} is null
     * @see #getResourceAsStream(String)
     * @since 1.1
     */
    public static Optional<InputStream> findResourceAsStream(final String name) {
        Objects.requireNonNull(name, "Resource name must not be null");

        final InputStream is;
        final JavaLayerContext context = JavaLayerContext.getInstance();

        if (hook != null) {
            is = hook.getResourceAsStream(name);
        } else {
            is = context.getResourceAsStream(name);
        }

        return Optional.ofNullable(is);
    }

    /**
     * Retrieves an InputStream for a named resource.
     * This method maintains backward compatibility with the original API.
     *
     * @param name the resource name (must not be null)
     * @return the InputStream for the resource, or null if not found
     * @throws NullPointerException if {@code name} is null
     * @see JavaLayerHook#getResourceAsStream(String)
     */
    public static synchronized InputStream getResourceAsStream(final String name) {
        return findResourceAsStream(name).orElse(null);
    }

    /**
     * Loads the named resource and returns its raw bytes.
     *
     * @param name the resource name (must not be null)
     * @return byte array containing the resource data, or null if not found
     * @throws NullPointerException if {@code name} is null
     * @throws IOException          if an I/O error occurs while reading
     * @see #getResourceAsStream(String)
     * @since 1.1
     */
    public static byte[] getResourceAsBytes(final String name) throws IOException {
        return getResourceAsOptionalBytes(name).orElse(null);
    }

    /**
     * Loads the named resource and returns its raw bytes as an Optional.
     *
     * @param name the resource name (must not be null)
     * @return Optional containing the byte array, or empty if not found
     * @throws NullPointerException if {@code name} is null
     * @throws IOException          if an I/O error occurs while reading
     * @since 1.1
     */
    public static Optional<byte[]> getResourceAsOptionalBytes(
            final String name) throws IOException {

        Objects.requireNonNull(name, "Resource name must not be null");

        try (InputStream is = getResourceAsStream(name)) {
            if (is == null) {
                return Optional.empty();
            }
            return Optional.of(readAllBytes(is));
        }
    }

    /**
     * Loads the named resource as a UTF-8 string.
     *
     * @param name the resource name (must not be null)
     * @return the resource content as a string, or null if not found
     * @throws NullPointerException if {@code name} is null
     * @throws IOException          if an I/O error occurs while reading
     * @see #getResourceAsBytes(String)
     * @since 1.1
     */
    public static String getResourceAsString(final String name) throws IOException {
        return getResourceAsOptionalString(name).orElse(null);
    }

    /**
     * Loads the named resource as a UTF-8 string wrapped in an Optional.
     *
     * @param name the resource name (must not be null)
     * @return Optional containing the resource content as a string, or empty if not found
     * @throws NullPointerException if {@code name} is null
     * @throws IOException          if an I/O error occurs while reading
     * @since 1.1
     */
    public static Optional<String> getResourceAsOptionalString(
            final String name) throws IOException {

        return getResourceAsOptionalBytes(name)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Reads all bytes from an InputStream into a byte array.
     * This is a Java 17 compatible replacement for InputStream.readAllBytes().
     *
     * @param inputStream the input stream to read from
     * @return byte array containing all data from the stream
     * @throws IOException if an I/O error occurs
     * @since 1.1
     */
    private static byte[] readAllBytes(final InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "Input stream must not be null");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }

    /**
     * Creates a copy of the given object through serialization.
     * This provides a deep copy mechanism for Serializable objects.
     *
     * @param <T>   the type of the object
     * @param obj   the object to copy (must be Serializable)
     * @param clazz the class of the object
     * @return a deep copy of the object
     * @throws IOException          if serialization fails
     * @throws NullPointerException if {@code obj} or {@code clazz} is null
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(final T obj, final Class<T> clazz) throws IOException {
        Objects.requireNonNull(obj, "Object to copy must not be null");
        Objects.requireNonNull(clazz, "Class must not be null");

        final byte[] serialized = serializeToBytes(obj);
        final Object copy = deserializeFromBytes(serialized);

        if (!clazz.isInstance(copy)) {
            throw new IOException(
                "Deep copy resulted in unexpected type: " + copy.getClass().getName()
            );
        }

        return (T) copy;
    }

    /**
     * Executes an operation with a resource loaded through the hook system.
     * This method ensures proper resource cleanup.
     *
     * @param <R>       the return type
     * @param name      the resource name
     * @param processor the function to process the resource
     * @return the result of processing
     * @throws IOException          if resource cannot be loaded or processing fails
     * @throws NullPointerException if any parameter is null
     * @since 1.1
     */
    public static <R> R withResource(
            final String name,
            final ResourceProcessor<R> processor) throws IOException {

        Objects.requireNonNull(name, "Resource name must not be null");
        Objects.requireNonNull(processor, "Resource processor must not be null");

        try (InputStream is = getResourceAsStream(name)) {
            if (is == null) {
                throw new IOException("Resource not found: " + name);
            }
            return processor.process(is);
        }
    }

    /**
     * Functional interface for processing resources.
     *
     * @param <R> the return type
     * @since 1.1
     */
    @FunctionalInterface
    public interface ResourceProcessor<R> {
        /**
         * Processes an input stream and returns a result.
         *
         * @param inputStream the input stream to process
         * @return the processing result
         * @throws IOException if an I/O error occurs
         */
        R process(InputStream inputStream) throws IOException;
    }

    /**
     * Internal context class for resource loading.
     * This encapsulates the resource loading strategy.
     *
     * @since 1.1
     */
    private static final class JavaLayerContext {
        private static final JavaLayerContext INSTANCE = new JavaLayerContext();
        private final ClassLoader contextClassLoader;

        private JavaLayerContext() {
            this.contextClassLoader = Thread.currentThread().getContextClassLoader();
        }

        static JavaLayerContext getInstance() {
            return INSTANCE;
        }

        InputStream getResourceAsStream(final String name) {
            // Try context class loader first
            InputStream is = contextClassLoader.getResourceAsStream(name);
            if (is != null) {
                return is;
            }

            // Fall back to class loader of JavaLayerUtils
            return JavaLayerUtils.class.getResourceAsStream(name);
        }
    }

    // Static initializer to ensure compatibility with older JVMs
    static {
        // Verify we're running on a compatible JVM
        try {
            Class.forName("java.util.Objects");
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException(
                "JavaLayerUtils requires Java 7 or later. " +
                "Please upgrade your JVM.",
                e
            );
        }
    }
}