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

import com.aurumsmods.littlebigio.BinaryInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pokémon Ranger uses flatbuffers to store data of any kind. They are binary buffers containing nested objects and structures
 * that are organized using offsets so that the game can access the data like any pointer-based data structure. Flatbuffers
 * consist of one or more labeled data sections. Data is accessed by searching for a section's label. Some flatbuffers may also
 * be compressed using the LZ10 algorithm.
 * 
 * @author Aurum
 */
public class FlatBuffer {
    /**
     * Returns a {@code FlatBuffer} as the result of reading and unpacking a supplied {@code File}.
     * 
     * @param file an input {@code File} to read from.
     * @return a {@code FlatBuffer} header containing the raw binary data sections.
     * @throws IOException if an error occurs during reading.
     * @throws LZ10.LZ10Exception if the buffer does not contain proper LZ10 data.
     */
    public static FlatBuffer unpackFlatBuffer(File file) throws IOException, LZ10.LZ10Exception {
        // Determine source input stream
        InputStream src;

        // Check if the file contains compressed data (LZ10)
        if (file.getName().endsWith(".cat")) {
            try (BinaryInputStream decompsrc = new BinaryInputStream(new FileInputStream(file), ByteOrder.LITTLE_ENDIAN)) {
                byte[] decompressed = LZ10.decompress(decompsrc);
                src = new ByteArrayInputStream(decompressed);
            }
        }
        // File is not compressed
        else
            src = new FileInputStream(file);

        FlatBuffer flatbuffer;
        try (BinaryInputStream in = new BinaryInputStream(src, ByteOrder.LITTLE_ENDIAN)) {
            flatbuffer = new FlatBuffer();
            flatbuffer.unpack(in);
        }
        return flatbuffer;
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    private ByteBuffer data = null;
    private int[] pointerFixList = null;
    private LinkedHashMap<String, Integer> sections = null;
    private LinkedHashMap<String, Integer> entries = null;
    private int unk14 = 0;
    
    private FlatBuffer() {
        // prevents instantiation, use unpackFlatBuffer instead
    }
    
    private void unpack(BinaryInputStream in) throws IOException {
        // Verify total file size
        int inputSize = in.available();
        int totalSize = in.readInt();
        if (inputSize < totalSize)
            throw new IOException("Input stream contains less bytes than expected.");
        
        // Read header
        int dataSize = in.readInt();
        int numPointers = in.readInt();
        int numSections = in.readInt();
        int numEntries = in.readInt();
        unk14 = in.readInt();
        in.skipNBytes(8); // remaining 8 bytes seem to be always set to 0
        
        // Read raw data block
        data = ByteBuffer.wrap(in.readNBytes(dataSize));
        data.order(ByteOrder.LITTLE_ENDIAN);
        
        // Read pointer fix list
        pointerFixList = new int[numPointers];
        
        for (int i = 0 ; i < numPointers ; i++)
            pointerFixList[i] = in.readInt();
        
        // Read raw label pairs
        int[] rawLabelPairs = new int[(numSections + numEntries) * 2];
        
        for (int i = 0 ; i < rawLabelPairs.length ; i += 2) {
            rawLabelPairs[i    ] = in.readInt();
            rawLabelPairs[i + 1] = in.readInt();
        }
        
        byte[] rawStrings = in.readNBytes(totalSize - (in.available() - inputSize));
        
        // Unpack sections and entries from label pairs
        sections = unpackLabelPairs(rawLabelPairs, rawStrings, 0, numSections);
        entries = unpackLabelPairs(rawLabelPairs, rawStrings, numSections * 2, numEntries);
    }
    
    private LinkedHashMap<String, Integer> unpackLabelPairs(int[] labelPairs, byte[] strPool, int firstIdx, int count) {
        LinkedHashMap<String, Integer> output = new LinkedHashMap(count);
        
        for (int i = 0 ; i < count ; i++) {
            // Fetch label pair data
            int index = firstIdx + i * 2;
            int offData = labelPairs[index];
            int offName = labelPairs[index + 1];
            
            // Read name string
            int lenName = 0;
            while(strPool[offName + lenName] != 0)
                lenName++;
            String name = new String(strPool, offName, lenName, StandardCharsets.US_ASCII);
            
            // Append labeled offset
            output.put(name, offData);
        }
        
        return output;
    }
    
    /**
     * Returns the raw data wrapped in a {@code ByteBuffer}.
     * 
     * @return the raw data wrapped in a {@code ByteBuffer}.
     */
    public ByteBuffer data() {
        return data;
    }
    
    /**
     * Returns the array of integer offsets to pointers. This is only used by the actual game to convert relative data offsets
     * into absolute address pointers.
     * 
     * @return the array of integer offsets to pointers.
     */
    public int[] getPointerFixList() {
        return pointerFixList;
    }
    
    /**
     * Returns the {@code Set} of name-offset section pairs.
     * 
     * @return the {@code Set} of name-offset section pairs.
     */
    public Set<Map.Entry<String, Integer>> sections() {
        return sections.entrySet();
    }
    
    /**
     * Returns the count of labeled sections.
     * 
     * @return the count of labeled sections.
     */
    public int sectionsCount() {
        return sections.size();
    }
    
    /**
     * Attempts to find and return the offset to the specified section. If the section cannot be found, -1 is returned instead.
     * 
     * @param name the name of the section to be searched
     * @return the offset to the specified section if available. Otherwise, -1 is returned.
     */
    public int findSection(String name) {
        Integer off = sections.get(name);
        if (off == null)
            return -1;
        return off;
    }
    
    /**
     * Returns the {@code Set} of name-offset entry pairs.
     * 
     * @return the {@code Set} of name-offset entry pairs.
     */
    public Set<Map.Entry<String, Integer>> entries() {
        return entries.entrySet();
    }
    
    /**
     * Returns the count of labeled entries.
     * 
     * @return the count of labeled entries.
     */
    public int entriesCount() {
        return entries.size();
    }
    
    /**
     * Attempts to find and return the offset to the specified entry. If the entry cannot be found, -1 is returned instead.
     * 
     * @param name the name of the entry to be searched
     * @return the offset to the specified entry if available. Otherwise, -1 is returned.
     */
    public int findEntry(String name) {
        Integer off = entries.get(name);
        if (off == null)
            return -1;
        return off;
    }
    
    /**
     * Returns the unknown constant value that is found at offset 0x14 in the flatbuffer header.
     * 
     * @return the unknown constant value at offset 0x14.
     */
    public int getUnk14() {
        return unk14;
    }
}
