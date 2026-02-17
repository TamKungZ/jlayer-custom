package javazoom.jl.modern;

import javazoom.jl.modern.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;

/**
 * Functional interface for modern playback listeners.
 * <p>
 * This can be used with lambda expressions:
 * <pre>{@code
 * ModernPlaybackListener listener = event -> {
 *     if (event.getId() == PlaybackEvent.STARTED) {
 *         System.out.println("Started at frame " + event.getFrame());
 *     }
 * };
 * }</pre>
 *
 * @see AdvancedPlayer
 */
@FunctionalInterface
public interface ModernPlaybackListener {
    /**
     * Called when a playback event occurs.
     * @param event the playback event
     */
    void onPlaybackEvent(PlaybackEvent event);

    /**
     * Creates an adapter that converts this listener to a traditional {@link PlaybackListener}.
     * @return a PlaybackListener instance
     */
    default PlaybackListener toLegacyListener() {
        return new PlaybackListener() {
            @Override
            public void playbackStarted(PlaybackEvent evt) {
                onPlaybackEvent(evt);
            }

            @Override
            public void playbackFinished(PlaybackEvent evt) {
                onPlaybackEvent(evt);
            }
        };
    }
}