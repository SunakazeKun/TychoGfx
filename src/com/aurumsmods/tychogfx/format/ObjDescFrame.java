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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * A single animation prerendered used by {@code ObjDesc} animations that stores the necessary animation to control the
 * sequencer. Instances of this class are created by {@code ObjDescParser} when loading a {@code FlatBuffer} containing
 * {@code ObjDesc} data sections. The prerendered sequences are then interpreted and executed by an {@code ObjDescSequencer}
 * instance.
 * 
 * @author Aurum
 */
public final class ObjDescFrame {
    /**
     * Specifies the animation sequencing type for a single prerendered.
     * @see ObjDescSequencer
     */
    public enum SeqType {
        /**
         * Advances to the next frame. If the frame is the last in the sequence, it resets to the first frame after the time
         * delay has passed.
         */
        LOOP_SEQUENCE,

        /**
         * Immediately cancels the animation sequence. The animation does not loop back to the beginning.
         * <p>
         * TODO: This needs to be tested again.
         */
        CANCEL_SEQUENCE,

        /**
         * Continues to the next frame if the previous subsequence has looped for the last specified number of times. Otherwise,
         * it loops back to the first frame of the subsequence
         */
        ELAPSE_LOOPS
    }
    
    /**
     * A constant denoting that some attribute node does not exist for a frame.
     */
    public static final int INVALID_ARGUMENT = 65536;
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    final static class Cell {
        ObjDescPalettes paletteContext;
        int paletteIdx, x, y, width, height;
        byte flipBits;
        byte[] bitmap;

        private void prerenderIntoFrame(BufferedImage frame, int offX, int offY) {
            int[] palette = paletteContext.getPalette(paletteIdx);
            int numPixels = bitmap.length * 2;
            
            for (int p = 0, colorID = 0 ; p < numPixels ; p++) {
                // Read a new byte every second pixel
                if ((p & 1) == 0)
                    colorID = bitmap[p >> 1];
                
                // Calculate absolute pixel position in the prerendered frame
                int rx = x - offX + mirror(p % width, (flipBits & 1) != 0, width);
                int ry = y - offY + mirror(p / width, (flipBits & 2) != 0, height);

                // Draw pixel if color is not the first in palette as these are always transparent
                if ((colorID & 15) != 0)
                    frame.setRGB(rx, ry, palette[colorID & 15]);
                
                // Retrieve upper nybble
                colorID >>>= 4;
            }
        }

        private int mirror(int val, boolean flip, int dist) {
            return flip ? dist - val - 1 : val;
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    // These are package-private and will be set by the parser.
    BufferedImage prerendered;
    Cell[] cells;
    SeqType seqType;
    int duration, shakeArgX, shakeArgY, unkShakeArgX, unkShakeArgY, loopCount;
    Rectangle bounds;
    
    /**
     * Constructs a new ObjDescFrame with the default parameters and no prerendered.
     */
    ObjDescFrame() {
        prerendered = null;
        cells = null;
        seqType = SeqType.LOOP_SEQUENCE;
        duration = INVALID_ARGUMENT;
        shakeArgX = INVALID_ARGUMENT;
        shakeArgY = INVALID_ARGUMENT;
        unkShakeArgX = INVALID_ARGUMENT;
        unkShakeArgY = INVALID_ARGUMENT;
        loopCount = INVALID_ARGUMENT;
        bounds = new Rectangle();
    }
    
    /**
     * Prerenders the frame by assembling the individual frame cells.
     */
    void prerender() {
        if (prerendered != null && cells != null) {
            for (Cell cell : cells)
                cell.prerenderIntoFrame(prerendered, bounds.x, bounds.y);
        }
    }
    
    /**
     * Returns the assembled animation prerendered image.
     * 
     * @return the animation prerendered image.
     */
    public BufferedImage prerendered() {
        return prerendered;
    }
    
    /**
     * Returns the animation prerendered sequence type for this specific prerendered.
     * 
     * @return the prerendered's sequence type.
     */
    public SeqType sequenceType() {
        return seqType;
    }
    
    /**
     * Returns the prerendered's duration until the sequence should advance to the next prerendered.
     * 
     * @return the prerendered's duration time.
     */
    public int duration() {
        return duration;
    }
    
    /**
     * Returns the argument for shaking along the X-axis.
     * 
     * @return the shaking argument for the X-axis.
     */
    public int shakeArgX() {
        return shakeArgX;
    }
    
    /**
     * Returns the argument for shaking along the Y-axis.
     * 
     * @return the shaking argument for the Y-axis.
     */
    public int shakeArgY() {
        return shakeArgY;
    }
    
    /**
     * Returns the unknown shaking argument for the X-axis.
     * 
     * @return the unknown shaking argument for the X-axis.
     */
    public int unkShakeArgX() {
        return unkShakeArgX;
    }
    
    /**
     * Returns the unknown shaking argument for the Y-axis.
     * 
     * @return the unknown shaking argument for the Y-axis.
     */
    public int unkShakeArgY() {
        return unkShakeArgY;
    }
    
    /**
     * Returns the number of loops that have to elapse before the sequencer is able to advance to the next prerendered after
     * {@code SeqType.ELAPSE_LOOPS}.
     * 
     * @return the number of loops that have to elapse.
     */
    public int loopCount() {
        return loopCount;
    }
    
    /**
     * Returns the bounds that specify the prerendered's relative offset in the sequence.
     * 
     * @return the bounds that specify the prerendered's relative offset.
     */
    public Rectangle bounds() {
        return bounds;
    }
}
