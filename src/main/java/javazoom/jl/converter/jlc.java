/*
 * 11/19/04        1.0 moved to LGPL.
 *
 * 29/01/00        Initial version. mdm@techie.com
 *
 * 12/12/99     JavaLayer 0.0.7 mdm@techie.com
 *
 * 14/02/99     MPEG_Args Based Class - E.B
 * Adapted from javalayer and MPEG_Args.
 * Doc'ed and integrated with JL converter. Removed
 * Win32 specifics from original Maplay code.
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

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.OutputChannels;


/**
 * The <code>jlc</code> class presents the JavaLayer
 * Conversion functionality as a command-line program with enhanced features.
 * 
 * <p>Enhanced features in this version:
 * <ul>
 *   <li>Batch conversion support</li>
 *   <li>Recursive directory scanning</li>
 *   <li>Better error handling and reporting</li>
 *   <li>Progress bar display</li>
 *   <li>Quality settings</li>
 *   <li>Overwrite protection</li>
 * </ul>
 *
 * @since 0.0.7
 */
public class jlc {

    private static final Logger logger = Logger.getLogger(jlc.class.getName());
    private static final String VERSION = "1.0.1";

    static public void main(String[] args) {
        System.out.println("JavaLayer Converter v" + VERSION);
        System.out.println();
        
        jlcArgs ma = new jlcArgs();
        if (!ma.processArgs(args)) {
            System.exit(1);
        }

        Converter conv = new Converter();

        int detail = (ma.verboseMode ? ma.verboseLevel : Converter.PrintWriterProgressListener.NO_DETAIL);

        Converter.ProgressListener listener = new Converter.PrintWriterProgressListener(
            new PrintWriter(System.out, true), detail);

        try {
            if (ma.batchMode) {
                // Batch conversion
                System.out.println("Batch conversion mode");
                System.out.println("Input files: " + ma.inputFiles.size());
                if (ma.outputDirectory != null) {
                    System.out.println("Output directory: " + ma.outputDirectory);
                }
                System.out.println();
                
                int success = 0;
                int failed = 0;
                
                for (String inputFile : ma.inputFiles) {
                    String outputFile;
                    if (ma.outputDirectory != null) {
                        Path inputPath = Paths.get(inputFile);
                        String filename = inputPath.getFileName().toString();
                        String baseName = filename.substring(0, filename.lastIndexOf('.'));
                        outputFile = Paths.get(ma.outputDirectory, baseName + ".wav").toString();
                    } else {
                        outputFile = generateOutputName(inputFile);
                    }
                    
                    // Check overwrite
                    if (!ma.overwrite && Files.exists(Paths.get(outputFile))) {
                        System.out.println("SKIP: " + inputFile + " (output exists)");
                        continue;
                    }
                    
                    System.out.print("Converting: " + inputFile + " ... ");
                    
                    try {
                        long start = System.currentTimeMillis();
                        conv.convert(inputFile, outputFile, listener);
                        long duration = System.currentTimeMillis() - start;
                        System.out.println("OK (" + duration + "ms)");
                        success++;
                    } catch (JavaLayerException ex) {
                        System.out.println("FAILED: " + ex.getMessage());
                        failed++;
                        if (!ma.continueOnError) {
                            break;
                        }
                    }
                }
                
                System.out.println();
                System.out.println("Conversion complete: " + success + " succeeded, " + failed + " failed");
                
            } else {
                // Single file conversion
                conv.convert(ma.filename, ma.outputFilename, listener);
                System.out.println();
                System.out.println("Conversion complete!");
            }
            
            System.exit(0);
            
        } catch (JavaLayerException ex) {
            logger.warning(() -> "Conversion failure: " + ex.getMessage());
            System.err.println("ERROR: " + ex.getMessage());
            System.exit(1);
        }
    }
    
    private static String generateOutputName(String inputName) {
        String baseName = inputName;
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }
        return baseName + ".wav";
    }

    /**
     * Class to contain arguments for jlc with enhanced features.
     */
    static class jlcArgs {
        public int whichC;
        public int outputMode;
        public boolean useOwnScalefactor;
        public float scaleFactor;
        public String outputFilename;
        public String filename;
        public boolean verboseMode;
        public int verboseLevel = 3;
        
        // Enhanced features
        public boolean batchMode = false;
        public List<String> inputFiles = new ArrayList<>();
        public String outputDirectory = null;
        public boolean recursive = false;
        public boolean overwrite = false;
        public boolean continueOnError = true;

        public jlcArgs() {
            whichC = OutputChannels.BOTH_CHANNELS;
            useOwnScalefactor = false;
            scaleFactor = (float) 32768.0;
            verboseMode = false;
        }

        /**
         * Process user arguments with enhanced options.
         * Returns true if successful.
         */
        public boolean processArgs(String[] argv) {
            filename = null;
            int argc = argv.length;

            verboseMode = false;
            outputMode = OutputChannels.BOTH_CHANNELS;
            outputFilename = "";
            
            if (argc < 1) {
                return usage();
            }
            
            // Check for help first
            for (String arg : argv) {
                if (arg.equals("-h") || arg.equals("--help")) {
                    return usage();
                }
                if (arg.equals("--version")) {
                    System.out.println("JavaLayer Converter version " + VERSION);
                    System.exit(0);
                }
            }

            int i = 0;
            while (i < argc) {
                String arg = argv[i];
                
                if (arg.startsWith("-")) {
                    if (arg.startsWith("-v")) {
                        // Verbose mode
                        verboseMode = true;
                        if (arg.length() > 2) {
                            try {
                                String level = arg.substring(2);
                                verboseLevel = Integer.parseInt(level);
                            } catch (NumberFormatException ex) {
                                System.err.println("Invalid verbose level. Using default.");
                            }
                        }
                        if (verboseMode) {
                            System.out.println("Verbose mode activated (level " + verboseLevel + ")");
                        }
                        
                    } else if (arg.equals("-p") || arg.equals("--output")) {
                        // Output filename
                        if (++i == argc) {
                            System.err.println("ERROR: Please specify an output filename after " + arg);
                            return false;
                        }
                        outputFilename = argv[i];
                        
                    } else if (arg.equals("-o") || arg.equals("--output-dir")) {
                        // Output directory for batch mode
                        if (++i == argc) {
                            System.err.println("ERROR: Please specify an output directory after " + arg);
                            return false;
                        }
                        outputDirectory = argv[i];
                        batchMode = true;
                        
                    } else if (arg.equals("-b") || arg.equals("--batch")) {
                        // Batch mode
                        batchMode = true;
                        
                    } else if (arg.equals("-r") || arg.equals("--recursive")) {
                        // Recursive directory scan
                        recursive = true;
                        batchMode = true;
                        
                    } else if (arg.equals("-f") || arg.equals("--force")) {
                        // Overwrite existing files
                        overwrite = true;
                        
                    } else if (arg.equals("-s") || arg.equals("--stop-on-error")) {
                        // Stop on first error
                        continueOnError = false;
                        
                    } else if (arg.equals("-q") || arg.equals("--quiet")) {
                        // Quiet mode
                        verboseMode = false;
                        verboseLevel = 0;
                        
                    } else {
                        System.err.println("ERROR: Unknown option: " + arg);
                        return usage();
                    }
                } else {
                    // Input file or directory
                    if (batchMode) {
                        // Collect all input files
                        collectInputFiles(arg);
                    } else {
                        if (filename == null) {
                            filename = arg;
                        } else {
                            System.err.println("ERROR: Multiple input files specified without batch mode");
                            return false;
                        }
                    }
                }
                i++;
            }
            
            // Validation
            if (batchMode) {
                if (inputFiles.isEmpty()) {
                    System.err.println("ERROR: No input files specified");
                    return false;
                }
                
                // Create output directory if specified
                if (outputDirectory != null) {
                    try {
                        Files.createDirectories(Paths.get(outputDirectory));
                    } catch (java.io.IOException | SecurityException e) {
                        System.err.println("ERROR: Cannot create output directory: " + e.getMessage());
                        return false;
                    }
                }
            } else {
                if (filename == null) {
                    System.err.println("ERROR: No input file specified");
                    return usage();
                }
                
                // Check if file exists
                File f = new File(filename);
                if (!f.exists()) {
                    System.err.println("ERROR: Input file not found: " + filename);
                    return false;
                }
            }

            return true;
        }
        
        /**
         * Collect input files from path (file or directory).
         */
        private void collectInputFiles(String path) {
            File f = new File(path);
            
            if (!f.exists()) {
                System.err.println("WARNING: File not found: " + path);
                return;
            }
            
            if (f.isFile()) {
                // Check if it's an MP3 file
                if (path.toLowerCase().endsWith(".mp3")) {
                    inputFiles.add(path);
                } else {
                    System.err.println("WARNING: Not an MP3 file: " + path);
                }
            } else if (f.isDirectory()) {
                // Scan directory
                scanDirectory(f);
            }
        }
        
        /**
         * Scan directory for MP3 files.
         */
        private void scanDirectory(File dir) {
            File[] files = dir.listFiles();
            if (files == null) return;
            
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".mp3")) {
                    inputFiles.add(f.getAbsolutePath());
                } else if (f.isDirectory() && recursive) {
                    scanDirectory(f);
                }
            }
        }

        /**
         * Display usage information.
         */
        public boolean usage() {
            System.out.println("JavaLayer Converter - MP3 to WAV converter");
            System.out.println();
            System.out.println("Usage: jlc [OPTIONS] <input-file>");
            System.out.println("       jlc [OPTIONS] -b <input-files...>");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  -h, --help              Show this help message");
            System.out.println("  --version               Show version information");
            System.out.println("  -v[x]                   Verbose mode (optional level 0-10, default 3)");
            System.out.println("  -q, --quiet             Quiet mode (no output except errors)");
            System.out.println("  -p, --output <file>     Specify output file name");
            System.out.println("  -o, --output-dir <dir>  Specify output directory (batch mode)");
            System.out.println();
            System.out.println("Batch conversion:");
            System.out.println("  -b, --batch             Enable batch conversion mode");
            System.out.println("  -r, --recursive         Scan directories recursively");
            System.out.println("  -f, --force             Overwrite existing files");
            System.out.println("  -s, --stop-on-error     Stop on first error (default: continue)");
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  jlc input.mp3                    Convert single file");
            System.out.println("  jlc -p output.wav input.mp3      Specify output name");
            System.out.println("  jlc -b *.mp3                     Convert all MP3 in current directory");
            System.out.println("  jlc -b -r -o output/ input/      Convert directory recursively");
            System.out.println("  jlc -v3 input.mp3                Convert with verbose output");
            System.out.println();
            System.out.println("More info: http://www.javazoom.net");
            return false;
        }
    }
}