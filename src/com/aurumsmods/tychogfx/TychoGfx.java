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
package com.aurumsmods.tychogfx;

import com.aurumsmods.ajul.ResourceLoader;
import com.aurumsmods.ajul.SwingUtil;
import java.awt.image.BufferedImage;

/**
 * Main class of this project. Specifies some important global constants and the entry point.
 * 
 * @author Aurum
 */
public final class TychoGfx {
    private TychoGfx() { throw new IllegalStateException(); }
    
    public static final ResourceLoader ASSET_LOADER = new ResourceLoader(TychoGfx.class);
    public static final BufferedImage PROGRAM_ICON = ASSET_LOADER.readImage("/assets/icon.png");
    
    public static final String AUTHOR = "Aurum";
    public static final String TITLE = "TychoGfx";
    public static final String LONG_TITLE = "TychoGfx -- Pokémon Ranger Gfx Viewer";
    public static final String VERSION = ASSET_LOADER.getBuiltDate();
    public static final String COPYRIGHT = "Copyright © 2021 Aurum";
    public static final String FULL_TITLE = String.join(" -- ", LONG_TITLE, VERSION, COPYRIGHT);
    
    public static void main(String[] args) {
        SwingUtil.trySetSystemUI();
        new TychoViewer().setVisible(true);
    }
}
