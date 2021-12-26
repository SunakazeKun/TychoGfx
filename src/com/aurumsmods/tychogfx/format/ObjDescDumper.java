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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Provides methods to dump animation sequences or individual animation frames as PNG images.
 * 
 * @author Aurum
 */
public final class ObjDescDumper {
    private ObjDescDumper() {
        throw new IllegalStateException("Instantiation of this class is forbidden!");
    }
    
    public static boolean dumpAnimationTypes(ObjDesc objdesc, String folder) throws IOException {
        boolean result = true;
        
        for (String type : objdesc.animationTypes())
            result |= dumpAnimationSequences(objdesc, type, folder);
        
        return result;
    }
    
    public static boolean dumpAnimationSequences(ObjDesc objdesc, String type, String folder) throws IOException {
        List<List<ObjDescFrame>> sequences = objdesc.animationSequences(type);
        
        if (sequences == null)
            return false;
        
        for (int seq = 0 ; seq < sequences.size() ; seq++)
            dumpAnimationSequence(sequences.get(seq), type, seq, folder);
        
        return true;
    }
    
    public static boolean dumpAnimationSequence(ObjDesc objdesc, String type, int seq, String folder) throws IOException {
        List<ObjDescFrame> sequence = objdesc.animationSequence(type, seq);
        
        if (sequence == null)
            return false;
        
        dumpAnimationSequence(sequence, type, seq, folder);
        return false;
    }
    
    public static boolean dumpAnimationFrame(ObjDesc objdesc, String type, int seq, int frameid, String folder) throws IOException {
        List<ObjDescFrame> sequence = objdesc.animationSequence(type, seq);
        
        if (sequence == null)
            return false;
        
        dumpAnimationFrame(sequence.get(frameid), type, seq, frameid, folder);
        return true;
    }
    
    private static void dumpAnimationSequence(List<ObjDescFrame> sequence, String type, int seq, String root) throws IOException {
        for (int frameid = 0 ; frameid < sequence.size() ; frameid++)
            dumpAnimationFrame(sequence.get(frameid), type, seq, frameid, root);
    }
    
    private static void dumpAnimationFrame(ObjDescFrame frame, String type, int seq, int frameid, String root) throws IOException {
        BufferedImage sprite = frame.prerendered();

        if (sprite != null) {
            File spriteFile = new File(String.format("%s/%s_%d_%d.png", root, type, seq, frameid));
            ImageIO.write(sprite, "png", spriteFile);
        }
    }
}
