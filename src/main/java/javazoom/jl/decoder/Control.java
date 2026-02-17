package javazoom.jl.decoder;

/**
 * <p>
 * Defines playback control behavior for an audio media source.
 * </p>
 *
 * <p>
 * Implementations typically control playback of MPEG Layer III (MP3)
 * streams decoded by the JLayer engine.
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   start() → isPlaying() == true
 *   pause() → isPlaying() == false
 *   stop()  → playback terminated, position reset or frozen (implementation dependent)
 * </pre>
 *
 * <h2>Position Semantics</h2>
 * <p>
 * Position is represented as a {@code double}. The unit is implementation
 * dependent but SHOULD represent:
 * </p>
 * <ul>
 *   <li>Seconds (preferred)</li>
 *   <li>Frame index</li>
 *   <li>Byte offset (legacy behavior)</li>
 * </ul>
 *
 * Implementations should document their position unit clearly.
 *
 * <h2>Thread Safety</h2>
 * This interface does not require thread safety. Implementations
 * should document their concurrency model.
 *
 * @author JLayer
 * @since 1.0
 */
public interface Control {

    /**
     * Starts or resumes playback.
     *
     * If playback is paused, this should resume from the current position.
     * If stopped, implementation may restart from beginning.
     */
    void start();

    /**
     * Stops playback.
     *
     * Implementations may:
     * <ul>
     *     <li>Reset position to zero</li>
     *     <li>Keep last position</li>
     * </ul>
     */
    void stop();

    /**
     * Returns whether playback is currently active.
     *
     * @return true if playing
     */
    boolean isPlaying();

    /**
     * Pauses playback.
     *
     * After calling pause(), {@link #isPlaying()} should return false.
     */
    void pause();

    /**
     * Returns whether the media supports random access (seeking).
     *
     * @return true if seeking is supported
     */
    boolean isRandomAccess();

    /**
     * Returns the current playback position.
     *
     * Unit is implementation specific (typically seconds).
     *
     * @return current position
     */
    double getPosition();

    /**
     * Sets playback position.
     *
     * Implementations should clamp values outside valid range.
     *
     * @param position new position
     */
    void setPosition(double position);

    /**
     * Toggles between play and pause states.
     *
     * Default implementation uses {@link #isPlaying()}.
     */
    default void toggle() {
        if (isPlaying()) {
            pause();
        } else {
            start();
        }
    }

    /**
     * Seeks relative to current position.
     *
     * @param delta amount to move (positive or negative)
     */
    default void seekRelative(double delta) {
        setPosition(getPosition() + delta);
    }

    /**
     * Returns true if playback is paused.
     *
     * Default assumes paused == not playing.
     *
     * @return true if paused
     */
    default boolean isPaused() {
        return !isPlaying();
    }
}
