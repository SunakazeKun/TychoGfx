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

final class ObjDescPalettes {
    private static final int[] DEFAULT_PALETTE = {
        0x00000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000,
        0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000
    };
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    final int[][] palettes;
    int offset = 0;
    
    ObjDescPalettes(int numpalettes) {
        palettes = new int[numpalettes][];
    }
    
    int[] getPalette(int index) {
        index += offset;
        
        if (0 <= index && index < palettes.length)
            return palettes[index];
        else
            return DEFAULT_PALETTE;
    }
}
