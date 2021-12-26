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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * A container storing the parsed animation sequences contained in a provided {@code FlatBuffer} object. Every sequence can be
 * retrieved using its type name and direction index. Before rendering any frame, one should make sure to invoke the function
 * {@code setPaletteOffsetAndPrerenderAllFrames} first. Otherwise, all frames will be invisible.
 * 
 * @author Aurum
 */
public final class ObjDesc {
    /**
     * Sorted array of all supported ObjDesc types.
     */
    private static final String[] OBJ_DESC_TYPES = {
        "abilityObjDesc",              // special ability
        "ability_bodyObjDesc",         // special ability for body parts (see pokemon/gyarados)
        "ability_neckObjDesc",         // special ability for neck parts (see pokemon/gyarados)
        "ability_sebireObjDesc",       // special ability for fin parts (see pokemon/gyarados)
        "ability_standObjDesc",        // smooth transition: special ability -> idle
        "ability_stand_bodyObjDesc",   // smooth transition for body parts: special ability -> idle (see pokemon/gyarados)
        "ability_stand_neckObjDesc",   // smooth transition for neck parts: special ability -> idle (see pokemon/gyarados)
        "ability_stand_sebireObjDesc", // smooth transition for fin parts: special ability -> idle (see pokemon/gyarados)
        "ability_stand_tailObjDesc",   // smooth transition for tail parts: special ability -> idle (see pokemon/gyarados)
        "ability_tailObjDesc",         // special ability tail parts (see pokemon/gyarados)
        "action2ObjDesc",              // special action 2
        "action3ObjDesc",              // special action 3
        "actionObjDesc",               // special action 1
        "akuObjDesc",                  // Dark-type capturing line (see line)
        "aliveObjDesc",                // ???
        "attack1ObjDesc",              // attack 1
        "attack1_standObjDesc",        // smooth transition: attack -> idle
        "attack2ObjDesc",              // attack 2
        "bgObjDesc",                   // ???
        "biribiriObjDesc",             // ??? (see mainmenu/DLminun)
        "boroObjDesc",                 // destroyed submarine (see player/zero1)
        "breakObjDesc",                // breaking target parts
        "catchObjDesc",                // Spencer calling Fearow (see player/leader_west)
        "charObjDesc",                 // ???
        "cut1ObjDesc",                 // cut target parts 1
        "cut2ObjDesc",                 // cut target parts 2
        "damageObjDesc",               // knockback (see player/hero)
        "denkiObjDesc",                // Electric-type capturing line (see line)
        "dislikeObjDesc",              // angered (see pokemon/haganeil)
        "dododamageObjDesc",           // riding Doduo knockback (see player/hero)
        "dodoriostandObjDesc",         // Joel riding Dodrio idle (see player/leader_east)
        "dodoriowalkObjDesc",          // Joel riding Dodrio walking (see player/leader_east)
        "dodostandObjDesc",            // riding Doduo idle (see player/hero)
        "dodowalkObjDesc",             // riding Doduo walking (see player/hero)
        "dokuObjDesc",                 // Poison-type capturing line (see line)
        "dragonObjDesc",               // Dragon-type capturing line (see line)
        "encountObjDesc",              // ???
        "espObjDesc",                  // Psychic-type capturing line (see line)
        "fallObjDesc",                 // falling (see player/hero)
        "flyObjDesc",                  // flying
        "fly_standObjDesc",            // smooth transition: flying -> idle
        "ghostObjDesc",                // Ghost-type capturing line (see line)
        "haganeObjDesc",               // Steel-type capturing line (see line)
        "hideObjDesc",                 // ??? (see flash)
        "hikouObjDesc",                // Flying-type capturing line (see line)
        "honooObjDesc",                // Fire-type capturing line (see line)
        "hoverObjDesc",                // hovering
        "hurimukiObjDesc",             // ??? (see mainmenu/DLminun)
        "iwaObjDesc",                  // Rock-type capturing line (see line)
        "jimenObjDesc",                // Ground-type capturing line (see line)
        "junbiObjDesc",                // ??? (see mainmenu/DLminun)
        "kakutouObjDesc",              // Fighting-type capturing line (see line)
        "kooriObjDesc",                // Ice-type capturing line (see line)
        "kusaObjDesc",                 // Grass-type capturing line (see line)
        "kyorokyoroObjDesc",           // ??? (see mainmenu/DLminun)
        "ladderObjDesc",               // climbing ladder (see player/hero)
        "ladder_downObjDesc",          // start climbing ladder down (see player/hero)
        "ladder_upObjDesc",            // start climbing ladder up (see player/hero)
        "landingObjDesc",              // landing after fall (see player/hero)
        "laplacestandObjDesc",         // riding Lapras idle (see player/hero)
        "laplacewalkObjDesc",          // riding Lapras walking (see player/hero)
        "mitsuketaObjDesc",            // ??? (see mainmenu/DLminun)
        "mizuObjDesc",                 // Water-type capturing line (see line)
        "musiObjDesc",                 // Bug-type capturing line (see line)
        "normalObjDesc",               // Capturing line (see line)
        "pose2ObjDesc",                // special pose 2
        "poseObjDesc",                 // special pose 1
        "pose_koObjDesc",              // ??? (see player/hero)
        "openObjDesc",                 // ???
        "pukaObjDesc",                 // repaired submarine (see player/zero1)
        "pushObjDesc",                 // ???
        "rideObjDesc",                 // player starting ride (see player/hero)
        "runObjDesc",                  // running
        "seikouObjDesc",               // ??? (see yakumono/DLmachine_hyouji)
        "shakeObjDesc",                // shaking target parts
        "sleepObjDesc",                // sleeping (see pokemon/haganeil)
        "stand2ObjDesc",               // idle 2
        "standObjDesc",                // idle
        "stand_abilityObjDesc",        // smooth transition: idle -> special ability
        "stand_abilityObjDesc",        // smooth transition: idle -> attack 1
        "stand_bodyObjDesc",           // idle for body parts
        "stand_flyObjDesc",            // smooth transition: idle -> airborne
        "stand_neckObjDesc",           // idle for neck parts
        "stand_sebireObjDesc",         // idle for fin parts (see pokemon/gyarados)
        "stand_tailObjDesc",           // idle for tail parts (see pokemon/haganeil, pokemon/gyarados)
        "stand_walkObjDesc",           // smooth transition: idle -> walk
        "type1ObjDesc",                // ???
        "walkObjDesc",                 // walking
        "walk_standObjDesc",           // smooth transition: walk -> idle
        "yattaObjDesc",                // ??? (see mainmenu/DLminun)
        "zannenObjDesc"                // ??? (see mainmenu/DLminun)
    };
    
    /**
     * Checks if the specified section name corresponds to the ObjDesc format. If it is, {@code true} will be returned, or else
     * {@code false} will be returned.
     * 
     * @param name the section name.
     * @return {@code true} if the section name corresponds to the ObjDesc format. Otherwise, {@code false} is returned.
     */
    public static boolean isObjDesc(String name) {
        return Arrays.binarySearch(OBJ_DESC_TYPES, name) >= 0;
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    /**
     * Returns a {@code ObjDesc} as the getAnimations of parsing the ObjDesc sections contained in a supplied
     * {@code FlatBuffer}. This container will hold all animations available in the specified flatbuffer.
     * 
     * @param flatbuffer the {@code FlatBuffer} that contains ObjDesc sections.
     * @return a {@code ObjDesc} containing the parsed animation sequences.
     */
    public static ObjDesc unpackObjDesc(FlatBuffer flatbuffer) {
        // Prepare the ObjDesc container that stores the parsed animations. The flatbuffer's section count also denotes the max
        // number of animation sequence types it can hold.
        ObjDesc objdesc = new ObjDesc(flatbuffer.sectionsCount());
        
        for (Entry<String, Integer> labeledSection: flatbuffer.sections()) {
            String sectionName = labeledSection.getKey();
            
            if (isObjDesc(sectionName)) {
                // Parse ObjDesc section
                int sectionOffset = labeledSection.getValue();
                ObjDescParser parser = new ObjDescParser(flatbuffer, sectionOffset);
                parser.parse();
                
                // Retrieve results
                objdesc.animations.put(sectionName, parser.getAnimations());
                objdesc.prerenderFrames.addAll(parser.getPrerenderFrames());
                objdesc.paletteContexts.add(parser.getPaletteContext());
            }
        }
        
        return objdesc;
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    // Although a linked node implementation is used by the actual game, we are using array lists to store our animation frame
    // sequences. This is really just for time-efficiency since we want to access our individual frames using indices.
    private final LinkedHashMap<String, List<List<ObjDescFrame>>> animations;
    
    // To slightly improve prerendering, all the necessary frames will be collected first to prevent nested loops.
    private final List<ObjDescFrame> prerenderFrames;
    private final List<ObjDescPalettes> paletteContexts;
    
    // The current palette offset that is prerendered.
    private int paletteOffset;
    
    /**
     * Constructs a new {@code ObjDesc} container that allocates enough space to fit the specified number of animation types.
     * 
     * @param allocanims number of animation types to allocate space for.
     */
    private ObjDesc(int allocanims) {
        animations = new LinkedHashMap(allocanims);
        prerenderFrames = new ArrayList(allocanims * 32);
        paletteContexts = new ArrayList(allocanims);
        paletteOffset = -1;
    }
    
    /**
     * Sets the palette offset for every frame. This also forces all frames to be prerendered again.
     * 
     * @param offset the palette offset.
     */
    public void setPaletteOffsetAndPrerenderAllFrames(int offset) {
        if (paletteOffset != offset) {
            paletteOffset = offset;
        
            for (ObjDescPalettes context : paletteContexts)
                context.offset = offset;

            for (ObjDescFrame frame : prerenderFrames)
                frame.prerender();
        }
    }
    
    /**
     * Returns a list of animation types that exist in this container.
     * 
     * @return a list of available animation types.
     */
    public List<String> animationTypes() {
        return List.copyOf(animations.keySet());
    }
    
    /**
     * Returns the list of animation sequences that correspond to the specified type.
     * 
     * @param type the animation sequence type to look for.
     * @return the list of animation sequences that correspond to the specified type.
     */
    public List<List<ObjDescFrame>> animationSequences(String type) {
        return animations.get(type);
    } 
    
    /**
     * Returns the animation sequence that corresponds to the specified type and direction.
     * 
     * @param type the animation sequence type.
     * @param direction the direction. A type may have 1, 4 or 8 directions specified.
     * @return the animation sequence that corresponds to the specified type and direction.
     */
    public List<ObjDescFrame> animationSequence(String type, int direction) {
        List<List<ObjDescFrame>> sequenceLists = animationSequences(type);
        
        if (sequenceLists == null)
            return null;
        
        return sequenceLists.get(direction);
    }
}
