/*
 * 11/19/04        1.0 moved to LGPL.
 * 01/12/99        Initial version.    mdm@techie.com
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code DecoderException} represents the class of errors that can occur
 * when decoding MPEG audio.
 * <p>
 * This exception provides detailed error information including:
 * </p>
 * <ul>
 *   <li>Error code from {@link DecoderErrors}</li>
 *   <li>Human-readable error message</li>
 *   <li>Optional root cause exception</li>
 *   <li>Optional context information (frame number, position, etc.)</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * try {
 *     decoder.decodeFrame(header, stream);
 * } catch (DecoderException e) {
 *     System.err.println("Error code: " + e.getErrorCode());
 *     System.err.println("Description: " + e.getErrorDescription());
 *     System.err.println("Category: " + e.getErrorCategory());
 *     
 *     if (e.getFrameNumber().isPresent()) {
 *         System.err.println("At frame: " + e.getFrameNumber().get());
 *     }
 *     
 *     // Check error type
 *     if (e.isLayerError()) {
 *         // Handle layer-specific errors
 *     } else if (e.isFormatError()) {
 *         // Handle format errors
 *     }
 * }
 * }</pre>
 *
 * @author MDM
 * @author Enhanced by modernization
 * @version 2.0
 * @since 0.0.5
 * @see DecoderErrors
 * @see Decoder
 */
public class DecoderException extends JavaLayerException implements DecoderErrors {

    private static final long serialVersionUID = 1L;

    /**
     * The error code indicating the specific type of error.
     */
    private final int errorCode;
    
    /**
     * Additional context information about the error.
     */
    private final Map<String, Object> context;
    
    /**
     * Frame number where the error occurred, if known.
     */
    private Long frameNumber;
    
    /**
     * Byte position in stream where error occurred, if known.
     */
    private Long streamPosition;

    /**
     * Constructs a new DecoderException with a message and optional cause.
     * <p>
     * The error code is set to {@link #UNKNOWN_ERROR}.
     * </p>
     * 
     * @param msg the error message
     * @param t the cause, or null
     */
    public DecoderException(String msg, Throwable t) {
        super(msg, t);
        this.errorCode = UNKNOWN_ERROR;
        this.context = new HashMap<>();
    }

    /**
     * Constructs a new DecoderException with an error code and optional cause.
     * <p>
     * The message is automatically generated from the error code.
     * </p>
     * 
     * @param errorCode the error code from {@link DecoderErrors}
     * @param t the cause, or null
     */
    public DecoderException(int errorCode, Throwable t) {
        this(getErrorString(errorCode), t, errorCode);
    }
    
    /**
     * Constructs a new DecoderException with a custom message and error code.
     * 
     * @param msg the error message
     * @param t the cause, or null
     * @param errorCode the error code
     * @since 2.0
     */
    public DecoderException(String msg, Throwable t, int errorCode) {
        super(msg, t);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }
    
    /**
     * Constructs a new DecoderException with just an error code.
     * 
     * @param errorCode the error code from {@link DecoderErrors}
     * @since 2.0
     */
    public DecoderException(int errorCode) {
        this(errorCode, null);
    }
    
    /**
     * Constructs a new DecoderException with a message only.
     * 
     * @param msg the error message
     * @since 2.0
     */
    public DecoderException(String msg) {
        this(msg, null);
    }

    /**
     * Gets the error code.
     * 
     * @return the error code from {@link DecoderErrors}
     */
    public int getErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets a human-readable description of the error code.
     * 
     * @return the error description
     * @since 2.0
     */
    public String getErrorDescription() {
        return DecoderErrors.getErrorDescription(errorCode);
    }
    
    /**
     * Gets the error category.
     * 
     * @return the category name (e.g., "Layer Error", "Format Error")
     * @since 2.0
     */
    public String getErrorCategory() {
        return DecoderErrors.getErrorCategory(errorCode);
    }
    
    /**
     * Checks if this is a decoder error.
     * 
     * @return true if in the decoder error range
     * @since 2.0
     */
    public boolean isDecoderError() {
        return DecoderErrors.isDecoderError(errorCode);
    }
    
    /**
     * Checks if this is a layer-specific error.
     * 
     * @return true if it's a layer error
     * @since 2.0
     */
    public boolean isLayerError() {
        return DecoderErrors.isLayerError(errorCode);
    }
    
    /**
     * Checks if this is a format/stream error.
     * 
     * @return true if it's a format error
     * @since 2.0
     */
    public boolean isFormatError() {
        return DecoderErrors.isFormatError(errorCode);
    }
    
    /**
     * Checks if this is a resource/memory error.
     * 
     * @return true if it's a resource error
     * @since 2.0
     */
    public boolean isResourceError() {
        return DecoderErrors.isResourceError(errorCode);
    }
    
    /**
     * Sets the frame number where the error occurred.
     * 
     * @param frameNumber the frame number
     * @return this exception for method chaining
     * @since 2.0
     */
    public DecoderException withFrameNumber(long frameNumber) {
        this.frameNumber = frameNumber;
        return this;
    }
    
    /**
     * Gets the frame number where the error occurred.
     * 
     * @return Optional containing the frame number, or empty if not set
     * @since 2.0
     */
    public Optional<Long> getFrameNumber() {
        return Optional.ofNullable(frameNumber);
    }
    
    /**
     * Sets the stream position where the error occurred.
     * 
     * @param position the byte position
     * @return this exception for method chaining
     * @since 2.0
     */
    public DecoderException withStreamPosition(long position) {
        this.streamPosition = position;
        return this;
    }
    
    /**
     * Gets the stream position where the error occurred.
     * 
     * @return Optional containing the position, or empty if not set
     * @since 2.0
     */
    public Optional<Long> getStreamPosition() {
        return Optional.ofNullable(streamPosition);
    }
    
    /**
     * Adds context information to the exception.
     * 
     * @param key the context key
     * @param value the context value
     * @return this exception for method chaining
     * @since 2.0
     */
    public DecoderException withContext(String key, Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        context.put(key, value);
        return this;
    }
    
    /**
     * Gets a context value.
     * 
     * @param key the context key
     * @return Optional containing the value, or empty if not found
     * @since 2.0
     */
    public Optional<Object> getContext(String key) {
        return Optional.ofNullable(context.get(key));
    }
    
    /**
     * Gets a context value with a specific type.
     * 
     * @param <T> the expected type
     * @param key the context key
     * @param type the expected class
     * @return Optional containing the typed value, or empty if not found or wrong type
     * @since 2.0
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getContext(String key, Class<T> type) {
        Object value = context.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }
    
    /**
     * Gets all context information.
     * 
     * @return unmodifiable map of context data
     * @since 2.0
     */
    public Map<String, Object> getAllContext() {
        return Map.copyOf(context);
    }
    
    /**
     * Checks if context information is available.
     * 
     * @return true if any context is set
     * @since 2.0
     */
    public boolean hasContext() {
        return !context.isEmpty();
    }

    /**
     * Gets a formatted error string with all available information.
     * 
     * @return a detailed error message
     * @since 2.0
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(getErrorCategory()).append(": ").append(getMessage());
        sb.append(" (code: 0x").append(Integer.toHexString(errorCode)).append(")");
        
        if (frameNumber != null) {
            sb.append("\n  Frame: ").append(frameNumber);
        }
        
        if (streamPosition != null) {
            sb.append("\n  Stream position: ").append(streamPosition);
        }
        
        if (!context.isEmpty()) {
            sb.append("\n  Context:");
            context.forEach((key, value) -> 
                sb.append("\n    ").append(key).append(": ").append(value)
            );
        }
        
        if (getCause() != null) {
            sb.append("\n  Caused by: ").append(getCause().getClass().getSimpleName());
            sb.append(": ").append(getCause().getMessage());
        }
        
        return sb.toString();
    }

    /**
     * Gets an error string for a given error code.
     * <p>
     * This method provides backward compatibility with the original implementation
     * while using the enhanced error description system.
     * </p>
     * 
     * @param errorcode the error code
     * @return a string describing the error
     */
    public static String getErrorString(int errorcode) {
        String description = DecoderErrors.getErrorDescription(errorcode);
        return description + " (0x" + Integer.toHexString(errorcode) + ")";
    }
    
    /**
     * Creates a new DecoderException builder for fluent construction.
     * 
     * @param errorCode the error code
     * @return a new builder
     * @since 2.0
     */
    public static Builder builder(int errorCode) {
        return new Builder(errorCode);
    }
    
    /**
     * Creates a new DecoderException builder with a message.
     * 
     * @param message the error message
     * @return a new builder
     * @since 2.0
     */
    public static Builder builder(String message) {
        return new Builder(message);
    }

    @Override
    public String toString() {
        return getDetailedMessage();
    }

    /**
     * Builder for creating DecoderException instances with fluent API.
     * 
     * @since 2.0
     */
    public static class Builder {
        private String message;
        private int errorCode = UNKNOWN_ERROR;
        private Throwable cause;
        private Long frameNumber;
        private Long streamPosition;
        private final Map<String, Object> context = new HashMap<>();
        
        private Builder(int errorCode) {
            this.errorCode = errorCode;
            this.message = DecoderErrors.getErrorDescription(errorCode);
        }
        
        private Builder(String message) {
            this.message = message;
        }
        
        /**
         * Sets the error code.
         * 
         * @param errorCode the error code
         * @return this builder
         */
        public Builder withErrorCode(int errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        
        /**
         * Sets the error message.
         * 
         * @param message the message
         * @return this builder
         */
        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }
        
        /**
         * Sets the cause.
         * 
         * @param cause the cause
         * @return this builder
         */
        public Builder withCause(Throwable cause) {
            this.cause = cause;
            return this;
        }
        
        /**
         * Sets the frame number.
         * 
         * @param frameNumber the frame number
         * @return this builder
         */
        public Builder atFrame(long frameNumber) {
            this.frameNumber = frameNumber;
            return this;
        }
        
        /**
         * Sets the stream position.
         * 
         * @param position the position
         * @return this builder
         */
        public Builder atPosition(long position) {
            this.streamPosition = position;
            return this;
        }
        
        /**
         * Adds context information.
         * 
         * @param key the key
         * @param value the value
         * @return this builder
         */
        public Builder withContext(String key, Object value) {
            context.put(key, value);
            return this;
        }
        
        /**
         * Builds the DecoderException.
         * 
         * @return the exception
         */
        public DecoderException build() {
            DecoderException ex = new DecoderException(message, cause, errorCode);
            
            if (frameNumber != null) {
                ex = ex.withFrameNumber(frameNumber);
            }
            
            if (streamPosition != null) {
                ex = ex.withStreamPosition(streamPosition);
            }
            
            context.forEach(ex::withContext);
            
            return ex;
        }
    }
}