/*
 * Copyright (C) 2021 Aurum
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aurumsmods.tychogfx.format;

import com.aurumsmods.ajul.io.BinaryInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

/**
 * A utility class that provides methods to decompress the contents of an LZ10 encoded input stream. LZ10 is an LZ-type format
 * that is commonly used in many post-GBC Nintendo games, including Pokémon Ranger. The compression information is stored in
 * little-endian byte order. Every LZ10 input stream starts off with a 0x10 {@code byte} value.
 * 
 * @author Aurum
 */
public final class LZ10 {
    /**
     * Signals than an error occured during the LZ10 decompression process.
     */
    public static class LZ10Exception extends Exception {
        /**
         * Constructs a new exception without a detail message.
         */
        public LZ10Exception() {
            super();
        }
        
        /**
         * Constructs a new exception with the specified detail message.
         * 
         * @param message the detail message.
         */
        public LZ10Exception(String message) {
            super(message);
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    private LZ10() {
        throw new IllegalStateException("Instantiation of this class is forbidden!");
    }
    
    /**
     * Reads and decompresses LZ10-compressed data from the given byte array. The decompressed data is returned in a new byte
     * array.
     * 
     * @param in the byte array containing LZ10 compressed data.
     * @return a new byte array containing the decompressed data.
     * @throws IOException if an error occurs during reading.
     * @throws LZ10Exception if the buffer does not contain proper LZ10 data.
     */
    public static byte[] decompress(byte[] in) throws IOException, LZ10Exception {
        return decompress(new BinaryInputStream(new ByteArrayInputStream(in), ByteOrder.LITTLE_ENDIAN));
    }
    
    /**
     * Reads and decompresses LZ10-compressed data from the given {@code InputStream}. The input stream will not be closed after
     * processing the data. The decompressed data is returned in a new byte array.
     * 
     * @param in the input stream containing LZ10 compressed data.
     * @return a new byte array containing the decompressed data.
     * @throws IOException if an error occurs during reading.
     * @throws LZ10Exception if the buffer does not contain proper LZ10 data.
     */
    public static byte[] decompress(InputStream in) throws IOException, LZ10Exception {
        return decompress(new BinaryInputStream(in, ByteOrder.LITTLE_ENDIAN));
    }
    
    /**
     * Reads and decompresses LZ10-compressed data from the given {@code BinaryInputStream}. The input stream will not be closed
     * after processing the data. The decompressed data is returned in a new byte array.
     * 
     * @param in the input stream containing LZ10 compressed data.
     * @return a new byte array containing the decompressed data.
     * @throws IOException if an error occurs during reading.
     * @throws LZ10Exception if the buffer does not contain proper LZ10 data.
     */
    public static byte[] decompress(BinaryInputStream in) throws IOException, LZ10Exception {
        // Check header and get output buffer size
        int lenOut = in.readInt();
        
        if ((lenOut & 0xFF) != 0x10)
            throw new LZ10Exception("Stream does not contain LZ10 compressed data.");
        lenOut >>= 8;
        
        // Prepare output buffer
        byte[] out = new byte[lenOut];
        int offOut = 0;
        
        // Decompress the stream's contents
        int block = 0; // The control block that describes how to decompress data, 8-bit
        int counter = 0; // Keeps track of the remaining bits to be checked for the current control block
        int b0, b1, dist, lenCopy, offCopy;
        
        while(offOut < lenOut) {
            // Get next control block. The bits are read starting from the most significant bit. If the bit is set, we read the
            // next two bytes that determine which decompressed bytes to copy into the output buffer. Otherwise, we copy the
            // next byte.
            if (counter == 0) {
                block = in.readUnsignedByte();
                counter = 8;
            }
            
            // Read next byte
            b0 = in.readUnsignedByte();
            
            // Is the most significant bit set? If so, copy decompressed data
            if ((block & 0x80) != 0) {
                // Read second byte
                b1 = in.readUnsignedByte();

                // Get copy offset and size
                dist = ((b0 & 0xF) << 8) | b1;
                offCopy = offOut - dist - 1;
                lenCopy = (b0 >> 4) + 3;

                // Copy 3+ bytes
                for (int i = 0 ; i < lenCopy ; i++)
                    out[offOut++] = out[offCopy + i];
            }
            // Otherwise, copy a plain byte into the output buffer.
            else
                out[offOut++] = (byte)b0;
            
            // Prepare next control block flag
            block <<= 1;
            counter--;
        }
        
        return out;
    }
}
