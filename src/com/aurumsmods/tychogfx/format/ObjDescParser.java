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

import com.aurumsmods.ajul.ColorUtil;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

final class ObjDescParser {
    // Working buffer attributes
    private ByteBuffer buffer;
    private final int originalBufferPos;
    
    // Output data structures that can be retrieved as a result.
    private List<ObjDescFrame> prerenderFrames;
    private ObjDescPalettes paletteContext;
    private List<List<ObjDescFrame>> animations;
    
    // Temporary storage for tile bitmaps. Since they will be reused several times, we don't want multiple copies of the same
    // data. This reduces wasting memory.
    private HashMap<Integer, byte[]> bitmaps;
    
    // Various important info header block attributes.
    private int offAnims, numAnims;           // Animations info
    private int offFirstFrame, unkFrameInfo;  // Frame info
    private int offTiles, rawTilesSize;       // Tiles info
    private int offPalettes, alignPalettes;   // Palettes info

    ObjDescParser(FlatBuffer flatbuffer, int offset) {
        // Prepare the working buffer and return offset
        buffer = flatbuffer.data();
        originalBufferPos = buffer.position();
        buffer.position(offset);
    }
    
    List<List<ObjDescFrame>> getAnimations() {
        return animations;
    }
    
    List<ObjDescFrame> getPrerenderFrames() {
        return prerenderFrames;
    }
    
    ObjDescPalettes getPaletteContext() {
        return paletteContext;
    }

    void parse() {
        // Parse header
        int offAnimInfo = buffer.getInt();
        int offFrameInfo = buffer.getInt();
        int offTileInfo = buffer.getInt();
        int offPaletteInfo = buffer.getInt();
        
        // Parse animations info
        buffer.position(offAnimInfo);
        offAnims = buffer.getInt();
        numAnims = buffer.getInt();
        numAnims = numAnims < 1 ? 1 : numAnims;
        
        // Frames info
        buffer.position(offFrameInfo);
        offFirstFrame = buffer.getInt();
        unkFrameInfo = buffer.getInt();
        
        // Parse tiles info
        buffer.position(offTileInfo);
        offTiles = buffer.getInt();
        rawTilesSize = buffer.getInt();
        
        // Parse palettes info
        buffer.position(offPaletteInfo);
        offPalettes = buffer.getInt();
        alignPalettes = buffer.getInt();
        
        // Parse palettes. As there is no field that declares the number of palettes in a file, a lot of the parsed palettes are
        // results of reading beyond the actual palette data.
        int numPalettes = alignPalettes / 0x20; // Approximate number of palettes
        paletteContext = new ObjDescPalettes(numPalettes);
        buffer.position(offPalettes);
        
        for (int p = 0 ; p < numPalettes ; p++) {
            int[] palette = new int[16];
            paletteContext.palettes[p] = palette;
            
            for (int i = 0 ; i < 16 ; i++)
                palette[i] = ColorUtil.BGR555ToARGB(buffer.getShort());
        }
        
        // Parse all animations
        bitmaps = new LinkedHashMap(rawTilesSize / 0x200); // Approximate initial capacity
        
        animations = new ArrayList(numAnims);
        prerenderFrames = new ArrayList(numAnims * 4);
        parseAnimations();
        
        // Restore the working buffer's initial position and drop the reference to it
        buffer.position(originalBufferPos);
        
        // Release some data that is not needed anymore
        buffer = null;
        bitmaps.clear();
        bitmaps = null;
    }
    
    private void parseAnimations() {
        for (int i = 0 ; i < numAnims ; i++) {
            // Parse animation sequence
            ArrayList<ObjDescFrame> sequence = new ArrayList();
            animations.add(sequence);
            
            int offAnim = buffer.getInt(offAnims + i * 4);
            int offAnimFrame;
            
            while((offAnimFrame = buffer.getInt(offAnim)) != 0) {
                sequence.add(parseAnimationFrame(offAnimFrame));
                offAnim += 4;
            }
        }
    }
    
    private ObjDescFrame parseAnimationFrame(int offAnimFrame) {
        buffer.position(offAnimFrame);
        
        // Parse the animation frame's attributes
        ObjDescFrame animFrame = new ObjDescFrame();
        boolean hasMoreAttributes = true;
        int cellsIdx = ObjDescFrame.INVALID_ARGUMENT;
        int type, param;
        
        while(hasMoreAttributes) {
            type = buffer.get() & 0xFF;
            param = buffer.get() == 0 ? ObjDescFrame.INVALID_ARGUMENT : buffer.getShort();
            
            switch(type) {
                // Duration & loop sequence
                case 1 -> {
                    animFrame.seqType = ObjDescFrame.SeqType.LOOP_SEQUENCE;
                    animFrame.duration = (short)param;
                    hasMoreAttributes = false;
                }
                
                // Cells index
                case 2 -> cellsIdx = param & 0xFFFF;
                
                // Shaking parameters
                case 3 -> animFrame.shakeArgX = (short)param;
                case 4 -> animFrame.shakeArgY = (short)param;
                case 5 -> animFrame.unkShakeArgX = (short)param;
                case 6 -> animFrame.unkShakeArgY = (short)param;
                
                // Loop count
                case 16 -> animFrame.loopCount = (short)param;
                
                // Elapse loop sequence
                case 32 -> {
                    animFrame.seqType = ObjDescFrame.SeqType.ELAPSE_LOOPS;
                    hasMoreAttributes = false;
                }
                
                // Unknown sequence
                case 80 -> {
                    animFrame.seqType = ObjDescFrame.SeqType.CANCEL_SEQUENCE;
                    hasMoreAttributes = false;
                }
            }
        }
        
        // If the frame has cells, parse them and prepare the prerender
        if (cellsIdx != ObjDescFrame.INVALID_ARGUMENT) {
            animFrame.cells = parseCells(cellsIdx);
            preparePrerender(animFrame);
        }
        
        return animFrame;
    }
    
    private ObjDescFrame.Cell[] parseCells(int cellsIdx) {
        // Read frame cells info
        buffer.position(offFirstFrame + cellsIdx * 8);
        int offCells = buffer.getInt();
        int numCells = buffer.getInt();
        
        // Parse all cells
        ObjDescFrame.Cell[] cells = new ObjDescFrame.Cell[numCells];
        
        for (int i = 0 ; i < numCells ; i++) {
            buffer.position(offCells + i * 12);
            
            ObjDescFrame.Cell cell = new ObjDescFrame.Cell();
            cells[i] = cell;
            
            // Parse cell
            int tileIndex = buffer.getShort() & 0xFFFF;
            cell.width = dimToPixels(buffer.get());
            cell.height = dimToPixels(buffer.get());
            cell.paletteIdx = (buffer.getShort() & 0xFFFF) / 0x20;
            cell.flipBits = buffer.get();
            buffer.get(); // padding
            cell.x = buffer.getShort();
            cell.y = buffer.getShort();
            
            // Set tile bitmap and palette context
            cell.bitmap = getTileBitmap(tileIndex);
            cell.paletteContext = paletteContext;
        }
        
        return cells;
    }
    
    private byte[] getTileBitmap(int tileIdx) {
        // Read tile info
        buffer.position(offTiles + tileIdx * 8);
        int offTile = buffer.getInt();
        int bitmapSize = buffer.getShort() & 0xFFFF;
        
        // Bitmap has been cached?
        if (bitmaps.containsKey(offTile))
            return bitmaps.get(offTile);
        // Read bitmap if not cached
        else {
            byte[] bitmap = new byte[bitmapSize];
            bitmaps.put(offTile, bitmap);
            buffer.position(offTile);
            buffer.get(bitmap);
            return bitmap;
        }
    }
    
    private int dimToPixels(int dim) {
        switch(dim) {
            case 0: return 8;
            case 1: return 16;
            case 2: return 32;
            case 3: return 64;
            default: throw new IllegalArgumentException(String.format("Unknown dimension %d", dim));
        }
    }
    
    private void preparePrerender(ObjDescFrame animFrame) {
        prerenderFrames.add(animFrame);
        
        // Calculate frame bounds in world space
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        
        for (ObjDescFrame.Cell cell : animFrame.cells) {
            int x = cell.x;
            int y = cell.y;
            int w = cell.width;
            int h = cell.height;
            
            if (x < minX)
                minX = x;
            if (y < minY)
                minY = y;
            if (x + w > maxX)
                maxX = x + w;
            if (y + h > maxY)
                maxY = y + h;
        }
        
        // Set the frame's actual bounds
        Rectangle bounds = animFrame.bounds;
        bounds.x = minX;
        bounds.y = minY;
        bounds.width = maxX - minX;
        bounds.height = maxY - minY;
        
        // Prepare the prerendered image, the actual prerendering is done later on
        animFrame.prerendered = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_4BYTE_ABGR);
    }
}
