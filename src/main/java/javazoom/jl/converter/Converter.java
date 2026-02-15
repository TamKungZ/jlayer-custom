/*
 * 11/19/04 1.0 moved to LGPL.
 * 12/12/99 Original version. mdm@techie.com.
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

package javazoom.jl.converter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.Obuffer;


/**
 * The <code>Converter</code> class implements the conversion of
 * an MPEG audio file to a .WAV file with enhanced features.
 * 
 * <p>Enhanced features in this version:
 * <ul>
 *   <li>Support for Path-based operations</li>
 *   <li>Cancellable conversions</li>
 *   <li>Better progress reporting with ETA</li>
 *   <li>Batch conversion support</li>
 *   <li>Error recovery options</li>
 *   <li>Automatic output naming</li>
 *   <li>Thread-safe operations</li>
 * </ul>
 *
 * @author MDM 12/12/99
 * @since 0.0.7
 */
public class Converter {

    /** Flag for cancellation support */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    
    /** Conversion statistics */
    private ConversionStats stats = new ConversionStats();

    /**
     * Creates a new converter instance.
     */
    public Converter() {
    }
    
    /**
     * Cancel ongoing conversion.
     */
    public void cancel() {
        cancelled.set(true);
    }
    
    /**
     * Check if conversion was cancelled.
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
    
    /**
     * Reset cancellation flag.
     */
    public void resetCancellation() {
        cancelled.set(false);
    }
    
    /**
     * Get conversion statistics.
     * @return statistics object
     */
    public ConversionStats getStats() {
        return stats;
    }

    public synchronized void convert(String sourceName, String destName) throws JavaLayerException {
        convert(sourceName, destName, null, null);
    }

    public synchronized void convert(String sourceName,
                                     String destName,
                                     ProgressListener progressListener) throws JavaLayerException {
        convert(sourceName, destName, progressListener, null);
    }
    
    /**
     * Convert using Path objects.
     * @param sourcePath source file path
     * @param destPath destination file path
     * @throws JavaLayerException if conversion fails
     */
    public synchronized void convert(Path sourcePath, Path destPath) throws JavaLayerException {
        convert(sourcePath, destPath, null, null);
    }
    
    /**
     * Convert using Path objects with progress listener.
     * @param sourcePath source file path
     * @param destPath destination file path
     * @param progressListener progress listener
     * @throws JavaLayerException if conversion fails
     */
    public synchronized void convert(Path sourcePath, Path destPath, 
                                    ProgressListener progressListener) throws JavaLayerException {
        convert(sourcePath, destPath, progressListener, null);
    }
    
    /**
     * Convert using Path objects with full options.
     * @param sourcePath source file path
     * @param destPath destination file path (null for auto-naming)
     * @param progressListener progress listener
     * @param decoderParams decoder parameters
     * @throws JavaLayerException if conversion fails
     */
    public synchronized void convert(Path sourcePath, Path destPath,
                                    ProgressListener progressListener,
                                    Decoder.Params decoderParams) throws JavaLayerException {
        Objects.requireNonNull(sourcePath, "Source path cannot be null");
        
        String destName;
        if (destPath != null) {
            destName = destPath.toString();
        } else {
            // Auto-generate output name
            destName = generateOutputName(sourcePath.toString());
        }
        
        convert(sourcePath.toString(), destName, progressListener, decoderParams);
    }

    public void convert(String sourceName,
                        String destName,
                        ProgressListener progressListener,
                        Decoder.Params decoderParams) throws JavaLayerException {
        if (destName == null || destName.isEmpty()) {
            destName = generateOutputName(sourceName);
        }
        
        try (InputStream in = openInput(sourceName)) {
            convert(in, destName, progressListener, decoderParams);
        } catch (IOException ioe) {
            throw new JavaLayerException(ioe.getLocalizedMessage(), ioe);
        }
    }
    
    /**
     * Generate output filename from input filename.
     * @param inputName input filename
     * @return output filename with .wav extension
     */
    private String generateOutputName(String inputName) {
        String baseName = inputName;
        
        // Remove extension
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }
        
        return baseName + ".wav";
    }

    public synchronized void convert(InputStream sourceStream,
                                     String destName,
                                     ProgressListener progressListener,
                                     Decoder.Params decoderParams) throws JavaLayerException {
        // Reset stats and cancellation
        stats = new ConversionStats();
        cancelled.set(false);
        
        if (progressListener == null) {
            progressListener = PrintWriterProgressListener.newStdOut(PrintWriterProgressListener.NO_DETAIL);
        }
        
        try {
            if (!(sourceStream instanceof BufferedInputStream)) {
                sourceStream = new BufferedInputStream(sourceStream);
            }
            
            int frameCount = -1;
            if (sourceStream.markSupported()) {
                sourceStream.mark(-1);
                frameCount = countFrames(sourceStream);
                sourceStream.reset();
            }
            
            stats.totalFrames = frameCount;
            progressListener.converterUpdate(ProgressListener.UPDATE_FRAME_COUNT, frameCount, 0);

            Obuffer output = null;
            Decoder decoder = new Decoder(decoderParams);
            Bitstream stream = new Bitstream(sourceStream);

            if (frameCount == -1) {
                frameCount = Integer.MAX_VALUE;
            }

            int frame = 0;
            long startTime = System.currentTimeMillis();
            stats.startTime = startTime;

            try {
                for (; frame < frameCount; frame++) {
                    // Check cancellation
                    if (cancelled.get()) {
                        progressListener.converterUpdate(ProgressListener.UPDATE_CANCELLED, 0, frame);
                        break;
                    }
                    
                    try {
                        Header header = stream.readFrame();
                        if (header == null) {
                            break;
                        }

                        progressListener.readFrame(frame, header);

                        if (output == null) {
                            int channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                            int freq = header.frequency();
                            output = new WaveFileObuffer(channels, freq, destName);
                            decoder.setOutputBuffer(output);
                            
                            stats.channels = channels;
                            stats.frequency = freq;
                        }

                        Obuffer decoderOutput = decoder.decodeFrame(header, stream);

                        if (decoderOutput != output) {
                            throw new InternalError("Output buffers are different.");
                        }

                        progressListener.decodedFrame(frame, header, output);

                        stream.closeFrame();
                        stats.framesProcessed = frame + 1;

                    } catch (JavaLayerException | InternalError ex) {
                        stats.errorCount++;
                        boolean stop = !progressListener.converterException(ex);

                        if (stop) {
                            throw new JavaLayerException(ex.getLocalizedMessage(), ex);
                        }
                    }
                }

            } finally {
                if (output != null) {
                    output.close();
                }
            }

            stats.endTime = System.currentTimeMillis();
            int time = (int) (stats.endTime - startTime);
            
            if (!cancelled.get()) {
                progressListener.converterUpdate(ProgressListener.UPDATE_CONVERT_COMPLETE, time, frame);
            }
            
        } catch (IOException ex) {
            throw new JavaLayerException(ex.getLocalizedMessage(), ex);
        }
    }
    
    /**
     * Batch convert multiple files.
     * @param sourceFiles array of source file paths
     * @param outputDir output directory (null for same as source)
     * @param progressListener progress listener
     * @return array of conversion results
     */
    public synchronized ConversionResult[] batchConvert(String[] sourceFiles, 
                                                       String outputDir,
                                                       ProgressListener progressListener) {
        ConversionResult[] results = new ConversionResult[sourceFiles.length];
        
        for (int i = 0; i < sourceFiles.length; i++) {
            String source = sourceFiles[i];
            String dest;
            
            if (outputDir != null) {
                Path sourcePath = Paths.get(source);
                String filename = sourcePath.getFileName().toString();
                dest = Paths.get(outputDir, generateOutputName(filename)).toString();
            } else {
                dest = generateOutputName(source);
            }
            
            try {
                long start = System.currentTimeMillis();
                convert(source, dest, progressListener, null);
                long duration = System.currentTimeMillis() - start;
                results[i] = new ConversionResult(source, dest, true, null, duration);
            } catch (JavaLayerException e) {
                results[i] = new ConversionResult(source, dest, false, e.getMessage(), 0);
            }
            
            // Check for cancellation
            if (cancelled.get()) {
                break;
            }
        }
        
        return results;
    }

    protected int countFrames(InputStream in) {
        return -1;
    }

    protected InputStream openInput(String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new IOException("File not found: " + fileName);
        }
        if (!file.canRead()) {
            throw new IOException("Cannot read file: " + fileName);
        }
        
        InputStream fileIn = Files.newInputStream(file.toPath());
        BufferedInputStream bufIn = new BufferedInputStream(fileIn);

        return bufIn;
    }

    /**
     * Statistics class for tracking conversion progress.
     */
    public static class ConversionStats {
        public int totalFrames = -1;
        public int framesProcessed = 0;
        public int errorCount = 0;
        public int channels = 0;
        public int frequency = 0;
        public long startTime = 0;
        public long endTime = 0;
        
        public long getDuration() {
            return endTime - startTime;
        }
        
        public double getProgress() {
            if (totalFrames <= 0) return 0.0;
            return (double) framesProcessed / totalFrames;
        }
        
        public long getEstimatedTimeRemaining() {
            if (totalFrames <= 0 || framesProcessed <= 0) return -1;
            long elapsed = System.currentTimeMillis() - startTime;
            double progress = getProgress();
            if (progress == 0) return -1;
            return (long) ((elapsed / progress) - elapsed);
        }
        
        @Override
        public String toString() {
            return String.format("Frames: %d/%d (%.1f%%), Errors: %d, Duration: %dms",
                framesProcessed, totalFrames, getProgress() * 100, errorCount, getDuration());
        }
    }
    
    /**
     * Result of a single conversion operation.
     */
    public static class ConversionResult {
        public final String sourcePath;
        public final String destPath;
        public final boolean success;
        public final String errorMessage;
        public final long duration;
        
        public ConversionResult(String sourcePath, String destPath, boolean success,
                               String errorMessage, long duration) {
            this.sourcePath = sourcePath;
            this.destPath = destPath;
            this.success = success;
            this.errorMessage = errorMessage;
            this.duration = duration;
        }
        
        @Override
        public String toString() {
            if (success) {
                return String.format("SUCCESS: %s -> %s (%dms)", sourcePath, destPath, duration);
            } else {
                return String.format("FAILED: %s - %s", sourcePath, errorMessage);
            }
        }
    }

    /**
     * This interface is used by the Converter to provide
     * notification of tasks being carried out by the converter.
     */
    public interface ProgressListener {
        int UPDATE_FRAME_COUNT = 1;
        int UPDATE_CONVERT_COMPLETE = 2;
        int UPDATE_CANCELLED = 3;

        void converterUpdate(int updateID, int param1, int param2);
        void parsedFrame(int frameNo, Header header);
        void readFrame(int frameNo, Header header);
        void decodedFrame(int frameNo, Header header, Obuffer o);
        boolean converterException(Throwable t);
    }

    /**
     * Implementation of <code>ProgressListener</code> with enhanced features.
     */
    static public class PrintWriterProgressListener implements ProgressListener {
        static public final int NO_DETAIL = 0;
        static public final int EXPERT_DETAIL = 1;
        static public final int VERBOSE_DETAIL = 2;
        static public final int DEBUG_DETAIL = 7;
        static public final int MAX_DETAIL = 10;

        private final PrintWriter pw;
        private final int detailLevel;
        private long lastUpdateTime = 0;
        private static final long UPDATE_INTERVAL = 1000; // 1 second

        static public PrintWriterProgressListener newStdOut(int detail) {
            return new PrintWriterProgressListener(new PrintWriter(System.out, true), detail);
        }

        public PrintWriterProgressListener(PrintWriter writer, int detailLevel) {
            this.pw = writer;
            this.detailLevel = detailLevel;
        }

        public boolean isDetail(int detail) {
            return (this.detailLevel >= detail);
        }

        @Override
        public void converterUpdate(int updateID, int param1, int param2) {
            if (isDetail(VERBOSE_DETAIL)) {
                switch (updateID) {
                    case UPDATE_CONVERT_COMPLETE -> {
                        if (param2 == 0) param2 = 1;
                        pw.println();
                        pw.println("Converted " + param2 + " frames in " + param1 + " ms (" + 
                                  (param1 / param2) + " ms per frame.)");
                    }
                    case UPDATE_CANCELLED -> {
                        pw.println();
                        pw.println("Conversion cancelled after " + param2 + " frames.");
                    }
                }
            }
        }

        @Override
        public void parsedFrame(int frameNo, Header header) {
            if ((frameNo == 0) && isDetail(VERBOSE_DETAIL)) {
                String headerString = header.toString();
                pw.println("File is a " + headerString);
            } else if (isDetail(MAX_DETAIL)) {
                String headerString = header.toString();
                pw.println("Parsed frame " + frameNo + ": " + headerString);
            }
        }

        @Override
        public void readFrame(int frameNo, Header header) {
            if ((frameNo == 0) && isDetail(VERBOSE_DETAIL)) {
                String headerString = header.toString();
                pw.println("File is a " + headerString);
            } else if (isDetail(MAX_DETAIL)) {
                String headerString = header.toString();
                pw.println("Read frame " + frameNo + ": " + headerString);
            }
        }

        @Override
        public void decodedFrame(int frameNo, Header header, Obuffer o) {
            if (isDetail(MAX_DETAIL)) {
                String headerString = header.toString();
                pw.println("Decoded frame " + frameNo + ": " + headerString);
                pw.println("Output: " + o);
            } else if (isDetail(VERBOSE_DETAIL)) {
                long currentTime = System.currentTimeMillis();
                
                if (frameNo == 0) {
                    pw.print("Converting");
                    pw.flush();
                    lastUpdateTime = currentTime;
                }

                // Update every second or every 10 frames
                if (currentTime - lastUpdateTime > UPDATE_INTERVAL || (frameNo % 10) == 0) {
                    pw.print('.');
                    pw.flush();
                    lastUpdateTime = currentTime;
                }
            }
        }

        @Override
        public boolean converterException(Throwable t) {
            if (this.detailLevel > NO_DETAIL) {
                t.printStackTrace(pw);
                pw.flush();
            }
            return false;
        }
    }
    
    /**
     * Silent progress listener that does nothing.
     */
    public static class SilentProgressListener implements ProgressListener {
        @Override
        public void converterUpdate(int updateID, int param1, int param2) {}
        
        @Override
        public void parsedFrame(int frameNo, Header header) {}
        
        @Override
        public void readFrame(int frameNo, Header header) {}
        
        @Override
        public void decodedFrame(int frameNo, Header header, Obuffer o) {}
        
        @Override
        public boolean converterException(Throwable t) {
            return false;
        }
    }
}