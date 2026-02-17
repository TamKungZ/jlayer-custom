package javazoom.jl.player.advanced;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import javazoom.jl.decoder.JavaLayerException;

/**
 * <p>
 * Command-line sample MP3 player using {@link AdvancedPlayer}.
 * </p>
 *
 * <p>
 * This class demonstrates asynchronous playback using a
 * {@link PlaybackListener}. It is intended as a minimal example
 * of how to use the advanced player API.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>
 *   java javazoom.jl.player.advanced.jlap file.mp3
 * </pre>
 *
 * <h2>Threading</h2>
 * Playback is executed in a separate thread to avoid blocking
 * the main thread.
 *
 * <h2>Backward Compatibility</h2>
 * This implementation preserves:
 * <ul>
 *   <li>Original class name</li>
 *   <li>Public method signatures</li>
 *   <li>System.exit() behavior</li>
 * </ul>
 *
 * @author JavaZoom
 * @since 1.0
 */
public class jlap {

    /**
     * Entry point for the sample player.
     *
     * @param args expects a single MP3 filename
     */
    public static void main(String[] args) {
        jlap test = new jlap();

        if (args.length != 1) {
            test.showUsage();
            System.exit(0);
        } else {
            try {
                test.play(args[0]);
            } catch (JavaLayerException | IOException ex) {
                System.err.println(ex.getMessage());
                System.exit(0);
            }
        }
    }

    /**
     * Plays the specified MP3 file.
     *
     * @param filename path to MP3 file
     * @throws JavaLayerException if decoding fails
     * @throws IOException if file access fails
     */
    public void play(String filename)
            throws JavaLayerException, IOException {

        InfoListener lst = new InfoListener();
        playMp3(new File(filename), lst);
    }

    /**
     * Prints usage information.
     */
    public void showUsage() {
        System.out.println("Usage: jla <filename>");
        System.out.println();
        System.out.println(
                " e.g. : java javazoom.jl.player.advanced.jlap localfile.mp3");
    }

    /**
     * Plays an MP3 file from beginning to end.
     *
     * @param mp3 MP3 file
     * @param listener playback listener
     * @return AdvancedPlayer instance
     * @throws IOException if file reading fails
     * @throws JavaLayerException if decoding fails
     */
    public static AdvancedPlayer playMp3(File mp3,
                                         PlaybackListener listener)
            throws IOException, JavaLayerException {
        return playMp3(mp3, 0, Integer.MAX_VALUE, listener);
    }

    /**
     * Plays a portion of an MP3 file.
     *
     * @param mp3 MP3 file
     * @param start starting frame
     * @param end ending frame
     * @param listener playback listener
     * @return AdvancedPlayer instance
     * @throws IOException if file reading fails
     * @throws JavaLayerException if decoding fails
     */
    public static AdvancedPlayer playMp3(File mp3,
                                         int start,
                                         int end,
                                         PlaybackListener listener)
            throws IOException, JavaLayerException {

        return playMp3(
                new BufferedInputStream(Files.newInputStream(mp3.toPath())),
                start,
                end,
                listener
        );
    }

    /**
     * Plays MP3 data from an input stream.
     *
     * Playback occurs asynchronously in a new thread.
     *
     * @param is input stream containing MP3 data
     * @param start starting frame
     * @param end ending frame
     * @param listener playback listener
     * @return AdvancedPlayer instance
     * @throws JavaLayerException if decoding fails
     */
    public static AdvancedPlayer playMp3(InputStream is,
                                         int start,
                                         int end,
                                         PlaybackListener listener)
            throws JavaLayerException {

        final AdvancedPlayer player = new AdvancedPlayer(is);
        player.setPlayBackListener(listener);

        new Thread(() -> {
            try {
                player.play(start, end);
            } catch (JavaLayerException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).start();

        return player;
    }

    /**
     * Simple playback listener printing playback information.
     */
    public static class InfoListener extends PlaybackListener {

        /**
         * Called when playback starts.
         *
         * @param evt playback event
         */
        @Override
        public void playbackStarted(PlaybackEvent evt) {
            System.out.println(
                    "Play started from frame " + evt.getFrame());
        }

        /**
         * Called when playback finishes.
         *
         * Terminates the JVM (original behavior preserved).
         *
         * @param evt playback event
         */
        @Override
        public void playbackFinished(PlaybackEvent evt) {
            System.out.println(
                    "Play completed at frame " + evt.getFrame());
            System.exit(0);
        }
    }
}
