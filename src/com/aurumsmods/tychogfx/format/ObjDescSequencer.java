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

import java.awt.Color;
import java.awt.Graphics;
import java.util.List;
import java.util.Objects;

/**
 * An animation sequencer for the {@code ObjDesc} graphics format that emulates the animation system found in Pokémon Ranger.
 * 
 * @author Aurum
 */
public final class ObjDescSequencer {
    private List<ObjDescFrame> currentAnimation;
    private ObjDescFrame currentFrame, currentRenderFrame;
    private int currentFrameIndex, firstSubSequenceFrameIndex, subloopCount, subloopThreshold, timer;
    private int shakingArgX, shakingArgY, currentShakeX, currentShakeY, unkShakeArgX, unkShakeArgY;
    
    /**
     * Constructs a new ObjDescSequencer without any active animation.
     */
    public ObjDescSequencer() {
        clearContext();
    }
    
    /**
     * Clears the current context and resets the sequencer's progress.
     */
    public void clearContext() {
        currentAnimation = null;
        currentFrame = null;
        currentRenderFrame = null;
        
        currentFrameIndex = 0;
        firstSubSequenceFrameIndex = 0;
        subloopCount = 0;
        subloopThreshold = 0;
        timer = 0;
        
        shakingArgX = 0;
        shakingArgY = 0;
        currentShakeX = 0;
        currentShakeY = 0;
        unkShakeArgX = 0;
        unkShakeArgY = 0;
    }
    
    /**
     * Sets the current animation sequence frame based on the specified animation type, sequence index and prerendered index.
     * 
     * @param objDesc the ObjDesc container.
     * @param animType the animation type to be used.
     * @param animIndex the animation sequence (orientation) to be used.
     * @param animFrameIndex the animation prerendered index to be used.
     */
    public void setSequenceAndFrame(ObjDesc objDesc, String animType, int animIndex, int animFrameIndex) {
        clearContext();

        currentAnimation = objDesc.animationSequence(animType, animIndex);
        Objects.requireNonNull(currentAnimation);
        Objects.checkIndex(animFrameIndex, currentAnimation.size());
        
        currentFrameIndex = animFrameIndex;
        firstSubSequenceFrameIndex = animFrameIndex;
        currentFrame = currentAnimation.get(animFrameIndex);
        updateArgsFromCurrentFrame();
    }
    
    /**
     * Returns the currently active {@code ObjDescFrame}.
     * 
     * @return the currently active {@code ObjDescFrame}.
     */
    public ObjDescFrame getCurrentFrame() {
        return currentFrame;
    }
    
    /**
     * Renders the current frame sprite in the specified graphics context. If enabled, it also renders the frame's bounding
     * boxes.
     * 
     * @param g the {@code Graphics} context to render into.
     * @param offX X offset.
     * @param offY Y offset.
     * @param showBoundingBox shows the frame's bounding box if {@code true}.
     */
    public void render(Graphics g, int offX, int offY, boolean showBoundingBox) {
        if (currentRenderFrame != null && currentRenderFrame.prerendered() != null) {
            int x = offX + currentShakeX;
            int y = offY + currentShakeY;
            int bx = currentRenderFrame.bounds.x + x;
            int by = currentRenderFrame.bounds.y + y;
            
            g.drawImage(currentRenderFrame.prerendered(), bx, by, null);
            
            if (showBoundingBox) {
                // Draw cell bounding boxes
                g.setColor(Color.BLUE);
                for (ObjDescFrame.Cell cell : currentRenderFrame.cells)
                    g.drawRect(x + cell.x, y + cell.y, cell.width, cell.height);
                
                // Draw frame bounding box
                g.setColor(Color.RED);
                g.drawRect(bx, by, currentRenderFrame.bounds.width, currentFrame.bounds.height);
            }
        }
    }
    
    /**
     * Updates the animation sequencer.
     */
    public void update() {
        if (currentAnimation == null)
            return;
        
        if (currentFrame.seqType == ObjDescFrame.SeqType.LOOP_SEQUENCE) {
            if (currentFrame.duration == ObjDescFrame.INVALID_ARGUMENT || timer >= currentFrame.duration) {
                advanceFrame();
                timer = 0;
            }
            else
                timer++;
        }
        
        if (currentFrame.seqType == ObjDescFrame.SeqType.ELAPSE_LOOPS) {
            if (++subloopCount >= subloopThreshold) {
                currentShakeX = 0;
                currentShakeY = 0;
                shakingArgX = 0;
                shakingArgY = 0;
                unkShakeArgX = 0;
                unkShakeArgY = 0;
                
                // It seems that the subloop count is never reset by the game which causes some subsequences to not loop as
                // originally intended. Hence, we comment out the code below to replicate this bug.
                //subloopCount = 0;
                subloopThreshold = 0;
                advanceFrame();
            }
            else
                resetSubSequence();
        }
        
        // TODO: Shaking is inaccurate and needs to be investigated!
        if ((timer % 4) == 0) {
            if (shakingArgY < 0)
                currentShakeY++;
            else if (shakingArgY > 0)
                currentShakeY--;
            
            if (shakingArgX < 0)
                currentShakeX--;
            else if (shakingArgX > 0)
                currentShakeX++;
        }
    }
    
    /**
     * Resets to the first frame of the currently active subsequence. 
     */
    private void resetSubSequence() {
        currentFrame = currentAnimation.get(firstSubSequenceFrameIndex);
        currentFrameIndex = firstSubSequenceFrameIndex;
        updateArgsFromCurrentFrame();
        timer = 0;
    }
    
    /**
     * Updates the active modifier arguments based on the current running frame. If an argument is unspecified or invalid, the
     * respective attribute will not be updated at all.
     */
    private void updateArgsFromCurrentFrame() {
        if (currentFrame.prerendered != null)
            currentRenderFrame = currentFrame;
        
        if (currentFrame.shakeArgX != ObjDescFrame.INVALID_ARGUMENT)
            shakingArgX = currentFrame.shakeArgX;
        if (currentFrame.shakeArgY != ObjDescFrame.INVALID_ARGUMENT)
            shakingArgY = currentFrame.shakeArgY;
        
        if (currentFrame.unkShakeArgX != ObjDescFrame.INVALID_ARGUMENT)
            unkShakeArgX = currentFrame.unkShakeArgX;
        if (currentFrame.unkShakeArgY != ObjDescFrame.INVALID_ARGUMENT)
            unkShakeArgX = currentFrame.unkShakeArgY;
        
        if (currentFrame.loopCount != ObjDescFrame.INVALID_ARGUMENT) {
            firstSubSequenceFrameIndex = (currentFrameIndex + 1) % currentAnimation.size();
            subloopThreshold = currentFrame.loopCount;
        }
    }
    
    /**
     * Advances to the next frame in the sequence. If the index exceeds the maximum number of frames in the sequence, it will be
     * reset to the very first frame in the sequence. Any shaking and subloop progress will be reset as well. It also updates
     * the currently active modifier arguments.
     */
    private void advanceFrame() {
        if (++currentFrameIndex >= currentAnimation.size()) {
            currentShakeX = 0;
            currentShakeY = 0;
            subloopCount = 0;
            currentFrameIndex = 0;
        }
        
        currentFrame = currentAnimation.get(currentFrameIndex);
        updateArgsFromCurrentFrame();
    }
}
