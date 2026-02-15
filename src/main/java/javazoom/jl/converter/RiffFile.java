/*
 * 11/19/04 1.0 moved to LGPL.
 * 02/23/99 JavaConversion by E.B
 * Don Cross, April 1993.
 * RIFF file format classes.
 * See Chapter 8 of "Multimedia Programmer's Reference" in
 * the Microsoft Windows SDK.
 *
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

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;


/**
 * Class to manage RIFF files with enhanced performance and features.
 * 
 * <p>Enhanced features in this version:
 * <ul>
 *   <li>Implements {@link Closeable} for try-with-resources support</li>
 *   <li>Uses NIO for better performance on large writes</li>
 *   <li>Thread-safe operations</li>
 *   <li>Better error handling and validation</li>
 *   <li>Support for Path-based operations</li>
 * </ul>
 * 
 * <p>Usage example:
 * <pre>{@code
 * try (RiffFile riff = new RiffFile()) {
 *     riff.open("output.wav", RiffFile.RFM_WRITE);
 *     // Write data...
 * } // Automatically closed
 * }</pre>
 */
public class RiffFile implements Closeable {

    public static class RiffChunkHeader {
        /** Four-character chunk ID */
        public int ckID = 0;
        /** Length of data in chunk */
        public int ckSize = 0;
        
        /** Copy constructor */
        public RiffChunkHeader() {}
        
        /** Constructor with values */
        public RiffChunkHeader(int ckID, int ckSize) {
            this.ckID = ckID;
            this.ckSize = ckSize;
        }
    }

    // DDCRET - Error codes

    /** The operation succeeded */
    public static final int DDC_SUCCESS = 0;
    /** The operation failed for unspecified reasons */
    public static final int DDC_FAILURE = 1;
    /** Operation failed due to running out of memory */
    public static final int DDC_OUT_OF_MEMORY = 2;
    /** Operation encountered file I/O error */
    public static final int DDC_FILE_ERROR = 3;
    /** Operation was called with invalid parameters */
    public static final int DDC_INVALID_CALL = 4;
    /** Operation was aborted by the user */
    public static final int DDC_USER_ABORT = 5;
    /** File format does not match */
    public static final int DDC_INVALID_FILE = 6;

    // RiffFileMode

    /** undefined type (can use to mean "N/A" or "not open") */
    public static final int RFM_UNKNOWN = 0;
    /** open for write */
    public static final int RFM_WRITE = 1;
    /** open for read */
    public static final int RFM_READ = 2;

    /** Default buffer size for NIO operations */
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    
    /** header for whole file */
    private RiffChunkHeader riffHeader;
    /** current file I/O mode */
    protected volatile int fmode;
    /** I/O stream to use */
    protected RandomAccessFile file;
    /** NIO channel for better performance */
    private FileChannel channel;
    /** Reusable buffer for NIO operations */
    private ByteBuffer writeBuffer;
    /** Lock object for thread safety */
    private final Object lock = new Object();

    /**
     * Dummy Constructor
     */
    public RiffFile() {
        file = null;
        fmode = RFM_UNKNOWN;
        riffHeader = new RiffChunkHeader();

        riffHeader.ckID = fourCC("RIFF");
        riffHeader.ckSize = 0;
        writeBuffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE);
        writeBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Return File Mode.
     */
    public int CurrentFileMode() {
        return fmode;
    }
    
    /**
     * Check if file is open.
     * @return true if file is open
     */
    public boolean isOpen() {
        return fmode != RFM_UNKNOWN && file != null;
    }
    
    /**
     * Get current file size.
     * @return file size in bytes, or -1 if error
     */
    public long getFileSize() {
        synchronized (lock) {
            if (!isOpen()) return -1;
            try {
                return file.length();
            } catch (IOException e) {
                return -1;
            }
        }
    }

    /**
     * Open a RIFF file using Path.
     * @param path the file path
     * @param newMode RFM_READ or RFM_WRITE
     * @return DDC_SUCCESS or error code
     */
    public int open(Path path, int newMode) {
        return Open(path.toString(), newMode);
    }

    /**
     * Open a RIFF file.
     */
    public int Open(String filename, int NewMode) {
        synchronized (lock) {
            int retcode = DDC_SUCCESS;

            if (fmode != RFM_UNKNOWN) {
                retcode = closeWithReturnCode();
            }

            if (retcode == DDC_SUCCESS) {
                switch (NewMode) {
                    case RFM_WRITE -> {
                        try {
                            file = new RandomAccessFile(filename, "rw");
                            channel = file.getChannel();

                            try {
                                // Write the RIFF header using NIO
                                ByteBuffer headerBuf = ByteBuffer.allocate(8);
                                headerBuf.order(ByteOrder.BIG_ENDIAN);
                                headerBuf.putInt(riffHeader.ckID);
                                headerBuf.order(ByteOrder.LITTLE_ENDIAN);
                                headerBuf.putInt(riffHeader.ckSize);
                                headerBuf.flip();
                                
                                while (headerBuf.hasRemaining()) {
                                    channel.write(headerBuf);
                                }
                                
                                fmode = RFM_WRITE;
                            } catch (IOException ioe) {
                                closeQuietly();
                                fmode = RFM_UNKNOWN;
                                retcode = DDC_FILE_ERROR;
                            }
                        } catch (IOException ioe) {
                            fmode = RFM_UNKNOWN;
                            retcode = DDC_FILE_ERROR;
                        }
                    }
                    case RFM_READ -> {
                        try {
                            file = new RandomAccessFile(filename, "r");
                            channel = file.getChannel();
                            
                            try {
                                // Try to read the RIFF header using NIO
                                ByteBuffer headerBuf = ByteBuffer.allocate(8);
                                int bytesRead = 0;
                                while (bytesRead < 8) {
                                    int read = channel.read(headerBuf);
                                    if (read == -1) {
                                        throw new IOException("Unexpected end of file");
                                    }
                                    bytesRead += read;
                                }
                                headerBuf.flip();
                                
                                headerBuf.order(ByteOrder.BIG_ENDIAN);
                                riffHeader.ckID = headerBuf.getInt();
                                headerBuf.order(ByteOrder.LITTLE_ENDIAN);
                                riffHeader.ckSize = headerBuf.getInt();
                                
                                fmode = RFM_READ;
                            } catch (IOException ioe) {
                                closeQuietly();
                                fmode = RFM_UNKNOWN;
                                retcode = DDC_FILE_ERROR;
                            }
                        } catch (IOException ioe) {
                            fmode = RFM_UNKNOWN;
                            retcode = DDC_FILE_ERROR;
                        }
                    }
                    default -> retcode = DDC_INVALID_CALL;
                }
            }
            return retcode;
        }
    }

    /**
     * Write numBytes data with enhanced performance using NIO.
     */
    public int write(byte[] data, int numBytes) {
        if (data == null || numBytes < 0 || numBytes > data.length) {
            return DDC_INVALID_CALL;
        }
        
        synchronized (lock) {
            if (fmode != RFM_WRITE) {
                return DDC_INVALID_CALL;
            }
            
            try {
                // For large writes, use NIO for better performance
                if (numBytes > 1024 && channel != null) {
                    ByteBuffer buffer = ByteBuffer.wrap(data, 0, numBytes);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                } else {
                    // For small writes, use traditional IO
                    file.write(data, 0, numBytes);
                }
                riffHeader.ckSize += numBytes;
                return DDC_SUCCESS;
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
        }
    }


    /**
     * Write short array with enhanced performance.
     */
    public int write(short[] data, int numBytes) {
        if (data == null || numBytes < 0) {
            return DDC_INVALID_CALL;
        }
        
        synchronized (lock) {
            if (fmode != RFM_WRITE) {
                return DDC_INVALID_CALL;
            }
            
            try {
                int numShorts = numBytes / 2;
                
                // Use NIO for better performance
                if (channel != null && numBytes > 512) {
                    ByteBuffer buffer = ByteBuffer.allocate(numBytes);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    
                    for (int i = 0; i < numShorts; i++) {
                        buffer.putShort(data[i]);
                    }
                    
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                } else {
                    // Traditional approach for compatibility
                    byte[] theData = new byte[numBytes];
                    int yc = 0;
                    for (int y = 0; y < numBytes; y += 2) {
                        theData[y] = (byte) (data[yc] & 0x00FF);
                        theData[y + 1] = (byte) ((data[yc++] >>> 8) & 0x00FF);
                    }
                    file.write(theData, 0, numBytes);
                }
                
                riffHeader.ckSize += numBytes;
                return DDC_SUCCESS;
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
        }
    }

    /**
     * Write RiffChunkHeader.
     */
    public int write(RiffChunkHeader triffHeader, int numBytes) {
        synchronized (lock) {
            if (fmode != RFM_WRITE) {
                return DDC_INVALID_CALL;
            }
            
            try {
                ByteBuffer buffer = ByteBuffer.allocate(8);
                buffer.order(ByteOrder.BIG_ENDIAN);
                buffer.putInt(triffHeader.ckID);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putInt(triffHeader.ckSize);
                buffer.flip();
                
                if (channel != null) {
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                } else {
                    byte[] br = new byte[8];
                    buffer.get(br);
                    file.write(br, 0, numBytes);
                }
                
                riffHeader.ckSize += numBytes;
                return DDC_SUCCESS;
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
        }
    }

    /**
     * Write short value.
     */
    public int write(short data, int numBytes) {
        synchronized (lock) {
            if (fmode != RFM_WRITE) {
                return DDC_INVALID_CALL;
            }
            
            try {
                // Little-endian write
                file.writeByte(data & 0xFF);
                file.writeByte((data >>> 8) & 0xFF);
                riffHeader.ckSize += numBytes;
                return DDC_SUCCESS;
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
        }
    }

    /**
     * Write int value.
     */
    public int write(int data, int numBytes) {
        synchronized (lock) {
            if (fmode != RFM_WRITE) {
                return DDC_INVALID_CALL;
            }
            
            try {
                // Little-endian write
                file.writeByte(data & 0xFF);
                file.writeByte((data >>> 8) & 0xFF);
                file.writeByte((data >>> 16) & 0xFF);
                file.writeByte((data >>> 24) & 0xFF);
                riffHeader.ckSize += numBytes;
                return DDC_SUCCESS;
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
        }
    }

    /**
     * Read numBytes data.
     */
    public int read(byte[] data, int numBytes) {
        if (data == null || numBytes < 0 || numBytes > data.length) {
            return DDC_INVALID_CALL;
        }
        
        synchronized (lock) {
            try {
                int totalRead = 0;
                while (totalRead < numBytes) {
                    int read = file.read(data, totalRead, numBytes - totalRead);
                    if (read == -1) {
                        return DDC_FILE_ERROR;
                    }
                    totalRead += read;
                }
                return DDC_SUCCESS;
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
        }
    }

    /**
     * Expect numBytes data.
     */
    public int expect(String data, int numBytes) {
        synchronized (lock) {
            int cnt = 0;
            try {
                while ((numBytes--) != 0) {
                    byte readByte = file.readByte();
                    if (readByte != data.charAt(cnt++)) return DDC_FILE_ERROR;
                }
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
            return DDC_SUCCESS;
        }
    }

    /**
     * Close Riff File.
     * Length is written too.
     * @return DDC_SUCCESS or error code
     * @deprecated Use {@link #close()} instead for AutoCloseable compatibility
     */
    @Deprecated
    public int closeWithReturnCode() {
        synchronized (lock) {
            int retcode = DDC_SUCCESS;

            switch (fmode) {
                case RFM_WRITE -> {
                    try {
                        file.seek(0);
                        
                        // Write final header with correct size
                        ByteBuffer headerBuf = ByteBuffer.allocate(8);
                        headerBuf.order(ByteOrder.BIG_ENDIAN);
                        headerBuf.putInt(riffHeader.ckID);
                        headerBuf.order(ByteOrder.LITTLE_ENDIAN);
                        headerBuf.putInt(riffHeader.ckSize);
                        headerBuf.flip();
                        
                        if (channel != null) {
                            while (headerBuf.hasRemaining()) {
                                channel.write(headerBuf);
                            }
                            channel.force(true); // Ensure data is written to disk
                        } else {
                            byte[] br = new byte[8];
                            headerBuf.get(br);
                            file.write(br, 0, 8);
                        }
                    } catch (IOException ioe) {
                        retcode = DDC_FILE_ERROR;
                    }
                }
                case RFM_READ -> {
                    // Nothing special for read mode
                }
            }
            
            closeQuietly();
            fmode = RFM_UNKNOWN;
            return retcode;
        }
    }
    
    /**
     * Close Riff File (Closeable interface implementation).
     * Throws IOException if there's an error during close.
     */
    @Override
    public void close() throws IOException {
        int retcode = closeWithReturnCode();
        if (retcode != DDC_SUCCESS) {
            throw new IOException("Failed to close RIFF file: " + toDDCRETString(retcode));
        }
    }
    
    /**
     * Close file resources without throwing exceptions.
     */
    private void closeQuietly() {
        try {
            if (channel != null) {
                channel.close();
                channel = null;
            }
        } catch (IOException ignored) {}
        
        try {
            if (file != null) {
                file.close();
                file = null;
            }
        } catch (IOException ignored) {}
    }

    /**
     * Return File Position.
     */
    public long currentFilePosition() {
        synchronized (lock) {
            try {
                return file != null ? file.getFilePointer() : -1;
            } catch (IOException ioe) {
                return -1;
            }
        }
    }

    /**
     * Write data to specified offset.
     */
    public int backpatch(long fileOffset, RiffChunkHeader data, int numBytes) {
        synchronized (lock) {
            if (file == null) {
                return DDC_INVALID_CALL;
            }
            try {
                file.seek(fileOffset);
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
            return write(data, numBytes);
        }
    }

    public int backpatch(long fileOffset, byte[] data, int numBytes) {
        synchronized (lock) {
            if (file == null) {
                return DDC_INVALID_CALL;
            }
            try {
                file.seek(fileOffset);
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
            return write(data, numBytes);
        }
    }

    /**
     * Seek in the File.
     */
    protected int seek(long offset) {
        synchronized (lock) {
            try {
                file.seek(offset);
                return DDC_SUCCESS;
            } catch (IOException ioe) {
                return DDC_FILE_ERROR;
            }
        }
    }

    /**
     * Error Messages.
     */
    public static String toDDCRETString(int retcode) {
        return switch (retcode) {
            case DDC_SUCCESS -> "DDC_SUCCESS";
            case DDC_FAILURE -> "DDC_FAILURE";
            case DDC_OUT_OF_MEMORY -> "DDC_OUT_OF_MEMORY";
            case DDC_FILE_ERROR -> "DDC_FILE_ERROR";
            case DDC_INVALID_CALL -> "DDC_INVALID_CALL";
            case DDC_USER_ABORT -> "DDC_USER_ABORT";
            case DDC_INVALID_FILE -> "DDC_INVALID_FILE";
            default -> "Unknown Error";
        };
    }
    
    /**
     * Validate error code and throw exception if error.
     * @param retcode the return code to validate
     * @throws IOException if retcode indicates an error
     */
    public static void validateReturnCode(int retcode) throws IOException {
        if (retcode != DDC_SUCCESS) {
            throw new IOException(toDDCRETString(retcode));
        }
    }

    /**
     * Fill the header.
     */
    public static int fourCC(String chunkName) {
        if (chunkName == null || chunkName.length() < 4) {
            throw new IllegalArgumentException("Chunk name must be at least 4 characters");
        }
        byte[] p = chunkName.getBytes();
        return (((p[0] << 24) & 0xFF000000) | 
                ((p[1] << 16) & 0x00FF0000) | 
                ((p[2] << 8) & 0x0000FF00) | 
                (p[3] & 0x000000FF));
    }
    
    /**
     * Get chunk size from RIFF header.
     * @return chunk size in bytes
     */
    public int getChunkSize() {
        return riffHeader.ckSize;
    }
    
    /**
     * Get chunk ID from RIFF header.
     * @return chunk ID as integer
     */
    public int getChunkID() {
        return riffHeader.ckID;
    }
    
    /**
     * Flush any buffered data to disk.
     * @return DDC_SUCCESS or error code
     */
    public int flush() {
        synchronized (lock) {
            if (!isOpen()) {
                return DDC_INVALID_CALL;
            }
            try {
                if (channel != null) {
                    channel.force(false);
                }
                return DDC_SUCCESS;
            } catch (IOException e) {
                return DDC_FILE_ERROR;
            }
        }
    }
}