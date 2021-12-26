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

import com.aurumsmods.ajul.lang.MathUtil;
import com.aurumsmods.ajul.util.SwingUtil;
import com.aurumsmods.tychogfx.format.FlatBuffer;
import com.aurumsmods.tychogfx.format.LZ10;
import com.aurumsmods.tychogfx.format.ObjDesc;
import com.aurumsmods.tychogfx.format.ObjDescDumper;
import com.aurumsmods.tychogfx.format.ObjDescFrame;
import com.aurumsmods.tychogfx.format.ObjDescSequencer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import javax.swing.Icon;
import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

final class TychoViewer extends javax.swing.JFrame {
    /**
     * These describe how the {@code PreviewPanel} and  {@code SequencerAndPreviewUpdater} should be used.
     */
    private enum UpdateMode {
        /**
         * Disables rendering and updating. Used when no {@code ObjDesc} is loaded.
         */
        NO_RENDERING,
        
        /**
         * Renders the current state of {@code ObjDescSequencer} but prevents updating by {@code SequencerAndPreviewUpdater}.
         */
        PAUSE,
        
        /**
         * Renders the current state of {@code ObjDescSequencer} and enables updating by {@code SequencerAndPreviewUpdater}.
         */
        RUN
    }
    
    private enum DumpMode {
        /**
         * Dumps all sequences for the selected category.
         */
        SEQUENCES,
        
        /**
         * Dumps the selected sequence and its frames.
         */
        SEQUENCE,
        
        // Dumps a selected individual prerendered.
        FRAME
    }
    
    /**
     * A panel that previews the {@code ObjDescSequencer}'s current animation. This also handles the scaling and drawing of the
     * orientation axes. Sprites will be drawn close to the center of the panel.
     */
    private final class PreviewPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            // Calculate width, height and center
            int scale = zoomFactor;
            int width = getWidth() / scale + 1;
            int height = getHeight() / scale + 1;
            int centerX = width / 2;
            int centerY = height / 2 + 32;
            
            // Properly clear canvas
            Rectangle clip = g.getClipBounds();
            g.setColor(backgroundColor);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            
            // Draw axes
            ((Graphics2D)g).scale(scale, scale);
            g.setColor(axisColor);
            g.drawLine(0, centerY, width, centerY);
            g.drawLine(centerX, 0, centerX, height);

            // Try render prerendered
            if (sequencerUpdateMode != UpdateMode.NO_RENDERING)
                objDescSequencer.render(g, centerX, centerY, btnShowBoundingBox.isSelected());
        }
    }
    
    /**
     * Updates the {@code ObjDescSequencer} and preview panel.
     */
    private final class SequencerAndPreviewUpdater implements Runnable {
        static final double UPDATE_RATE = 1.0d / 67.0d;
        
        final AtomicBoolean running = new AtomicBoolean(false);
        
        @Override
        public void run() {
            long current, last = System.currentTimeMillis();
            double delta = 0;
            boolean render = false;
            
            while(running.get()) {
                current = System.currentTimeMillis();
                delta += (current - last) / 1000.0d;
                last = current;
                
                while(delta > UPDATE_RATE) {
                    if (sequencerUpdateMode == UpdateMode.RUN) {
                        objDescSequencer.update();
                        render = true;
                        updateCurrentFrameInfo();
                    }
                    
                    delta -= UPDATE_RATE;
                }
                
                if (render) {
                    render = false;
                    preview.repaint();
                }
            }
        }
    }
    
    /**
     * An implementation of {@code DefaultMutableTreeNode} that stores information about what animation object it represents.
     */
    private static final class ObjDescEntryNode extends DefaultMutableTreeNode {
        final UpdateMode sequencerUpdateMode;
        final DumpMode dumpMode;
        final int iconId;
        final String animType;
        final int animIndex, animFrameIndex;

        ObjDescEntryNode(Object disp, UpdateMode sequpdate, DumpMode dump, int iconid, String type, int seq, int frameid) {
            super(disp);
            sequencerUpdateMode = sequpdate;
            dumpMode = dump;
            iconId = iconid;
            animType = type;
            animIndex = seq;
            animFrameIndex = frameid;
        }
    }
    
    /**
     * The tree cell renderer which applies the proper icons to each animation object node.
     */
    private static final class ObjDescTreeCellRenderer extends DefaultTreeCellRenderer {
        static final Icon[] icons = {
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_root.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_animtype.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_animframe.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_anim_seq_0.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_anim_seq_1.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_anim_seq_2.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_anim_seq_3.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_anim_seq_4.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_anim_seq_5.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_anim_seq_6.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_anim_seq_7.png"),
            TychoGfx.ASSET_LOADER.readIcon("/assets/img/node_anim_seq_any.png")
        };
        
        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            
            if (value instanceof ObjDescEntryNode)
                setIcon(icons[((ObjDescEntryNode)value).iconId]);
            else
                setIcon(icons[0]);
            
            return this;
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    private File gfxFile;
    private ObjDesc objDesc;
    private final ObjDescSequencer objDescSequencer;
    
    private UpdateMode sequencerUpdateMode;
    private final SequencerAndPreviewUpdater updater;
    private final Thread updaterThread;
    private final PreviewPanel preview;
    private int zoomFactor;
    private Color backgroundColor, axisColor;
    
    public TychoViewer() {
        gfxFile = null;
        objDesc = null;
        objDescSequencer = new ObjDescSequencer();
        
        sequencerUpdateMode = UpdateMode.NO_RENDERING;
        updater = new SequencerAndPreviewUpdater();
        updaterThread = new Thread(updater, "SequencerAndPreviewUpdater");
        preview = new PreviewPanel();
        zoomFactor = 2;
        backgroundColor = Color.WHITE;
        axisColor = Color.GRAY;
        
        initComponents();
        updateCurrentFrameInfo();
        splitInner.setRightComponent(preview);
    }
    
    @Override
    public void dispose() {
        updater.running.set(false);
        super.dispose();
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    private void openObjDescFile() {
        final JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open Pokémon Ranger flatbuffer...");
        fc.setFileFilter(new FileNameExtensionFilter("Pokémon Ranger flatbuffer (*.dat, *.cat)", "dat", "cat"));
        fc.setSelectedFile(new File(Preferences.userRoot().get("tychogfx_lastFile", "")));
        
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            gfxFile = fc.getSelectedFile();
            
            if (gfxFile.isFile()) {
                Preferences.userRoot().put("tychogfx_lastFile", gfxFile.getPath());
                loadObjDesc();
            }
            else
                gfxFile = null;
        }
    }
    
    private void loadObjDesc() {
        // Reset sequencer and disable rendering/updating
        objDesc = null;
        objDescSequencer.clearContext();
        sequencerUpdateMode = UpdateMode.NO_RENDERING;
        updateCurrentFrameInfo();
        
        // Try to unpack ObjDesc graphics and create the tree nodes representation
        try {
            objDesc = ObjDesc.unpackObjDesc(FlatBuffer.unpackFlatBuffer(gfxFile));
            objDesc.setPaletteOffsetAndPrerenderAllFrames(0);
            populateSequenceNodes();
        }
        catch(IOException | LZ10.LZ10Exception ex) {
            gfxFile = null;
            objDesc = null;
            SwingUtil.showExceptionBox(this, ex, TychoGfx.TITLE);
        }
        
        // Enable and reset palette slider
        sldPalette.setEnabled(objDesc != null);
        sldPalette.setValue(0);
    }
    
    private static int translateDirection(int index, int seqcount) {
        if (seqcount == 4)
            return index * 2;
        else if (seqcount == 8)
            return index;
        return 8;
    }
    
    private void populateSequenceNodes() {
        // Create root node which displays the filename
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(gfxFile.getName());
        
        // Create all anim type nodes
        for (String animType : objDesc.animationTypes()) {
            List<List<ObjDescFrame>> animSeqs = objDesc.animationSequences(animType);
            int numAnimSeqs = animSeqs.size();
            
            ObjDescEntryNode animTypeNode = new ObjDescEntryNode(
                    animType.replaceAll("ObjDesc", ""), // display ObjDesc type
                    UpdateMode.RUN,                     // enables updating/rendering
                    DumpMode.SEQUENCES,                 // dump all sequences for the animation type
                    1,                                  // uses node_animtype icon
                    animType,                           // animation sequence type
                    0,                                  // animation sequence
                    0                                   // animation sequence starting prerendered
            );
            root.add(animTypeNode);
            
            // Create anim sequence nodes
            for (int seq = 0 ; seq < numAnimSeqs ; seq++) {
                List<ObjDescFrame> animSeq = animSeqs.get(seq);
                int transDirIdx = translateDirection(seq, numAnimSeqs);
                
                ObjDescEntryNode animSeqNode = new ObjDescEntryNode(
                        String.format("Direction %d", seq), // displays direction index string
                        UpdateMode.RUN,                     // enables updating/rendering
                        DumpMode.SEQUENCE,                  // dump an entire animation sequence
                        3 + transDirIdx,                    // uses node_anim_seq_X icon
                        animType,                           // animation sequence type
                        seq,                                // animation sequence
                        0                                   // animation sequence starting prerendered
                );
                animTypeNode.add(animSeqNode);
                
                // Create anim prerendered nodes
                for (int frame = 0 ; frame < animSeq.size() ; frame++) {
                    ObjDescEntryNode animFrameNode = new ObjDescEntryNode(
                            String.format("Frame %d", frame), // displays prerendered index string
                            UpdateMode.PAUSE,                 // disables updating
                            DumpMode.FRAME,                   // dump a single prerendered
                            2,                                // uses node_animframe icon
                            animType,                         // animation sequence type
                            seq,                              // animation sequence
                            frame                             // animation sequence starting prerendered
                    );
                    animSeqNode.add(animFrameNode);
                }
            }
        }
        
        // Update actual tree model
        ((DefaultTreeModel)treeNodes.getModel()).setRoot(root);
    }
    
    private String chooseDumpFolderPath() throws IOException {
        final JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select output path...");
        fc.setCurrentDirectory(new File(Preferences.userRoot().get("tychogfx_lastDumpFolder", "")));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = fc.getSelectedFile();
            Preferences.userRoot().put("tychogfx_lastDumpFolder", selectedFolder.getAbsolutePath());
            
            // Get ObjDesc filename without extension
            String gfxFileName = gfxFile.getName();
            gfxFileName = gfxFileName.substring(0, gfxFileName.lastIndexOf('.'));
            
            File folder = new File(String.format("%s/%s", selectedFolder.getAbsolutePath(), gfxFileName));
            
            // Try to create the file if necessary
            if (!folder.exists())
                folder.mkdir();
            else if (folder.isFile())
                throw new IOException();
            
            return folder.getAbsolutePath();
        }
        
        return null;
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    private void tryZoom(int arg) {
        zoomFactor = MathUtil.clamp(1, 10, zoomFactor + arg);
        
        if (sequencerUpdateMode != UpdateMode.RUN)
            preview.repaint();
    }
    
    private void updateCurrentFrameAttribute(JTextField text, int value) {
        text.setText(value == ObjDescFrame.INVALID_ARGUMENT ? "n/a" : String.valueOf(value));
    }
    
    private void updateCurrentFrameInfo() {
        ObjDescFrame frame = objDescSequencer.getCurrentFrame();
        
        if (frame == null) {
            txtHasCells.setText("n/a");
            txtHasCells.setForeground(Color.BLACK);
            txtSequencing.setText("n/a");
            txtDuration.setText("n/a");
            txtShakeArgX.setText("n/a");
            txtShakeArgY.setText("n/a");
            txtUnkShakeArgX.setText("n/a");
            txtUnkShakeArgY.setText("n/a");
            txtLoopCount.setText("n/a");
        }
        else {
            boolean hasCells = frame.prerendered() != null;
            txtHasCells.setText(hasCells ? "TRUE" : "FALSE");
            txtHasCells.setForeground(hasCells ? Color.GREEN : Color.RED);
            
            txtSequencing.setText(frame.sequenceType().name());
            updateCurrentFrameAttribute(txtDuration, frame.duration());
            updateCurrentFrameAttribute(txtShakeArgX, frame.shakeArgX());
            updateCurrentFrameAttribute(txtShakeArgY, frame.shakeArgY());
            updateCurrentFrameAttribute(txtUnkShakeArgX, frame.unkShakeArgX());
            updateCurrentFrameAttribute(txtUnkShakeArgY, frame.unkShakeArgY());
            updateCurrentFrameAttribute(txtLoopCount, frame.loopCount());
        }
    }
    
    // -------------------------------------------------------------------------------------------------------------------------
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        toolbar = new javax.swing.JToolBar();
        btnExportFrames = new javax.swing.JButton();
        sep1 = new javax.swing.JToolBar.Separator();
        btnZoomOut = new javax.swing.JButton();
        btnZoomIn = new javax.swing.JButton();
        btnShowBoundingBox = new javax.swing.JToggleButton();
        sep2 = new javax.swing.JToolBar.Separator();
        btnSetBackgroundColor = new javax.swing.JButton();
        btnSetAxisColor = new javax.swing.JButton();
        sep3 = new javax.swing.JToolBar.Separator();
        lblPalette = new javax.swing.JLabel();
        sldPalette = new javax.swing.JSlider();
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        splitOuter = new javax.swing.JSplitPane();
        splitInner = new javax.swing.JSplitPane();
        scrollNodes = new javax.swing.JScrollPane();
        treeNodes = new javax.swing.JTree();
        pnlFrameInfo = new javax.swing.JPanel();
        lblSequencing = new javax.swing.JLabel();
        lblDuration = new javax.swing.JLabel();
        lblShakeArgX = new javax.swing.JLabel();
        lblShakeArgY = new javax.swing.JLabel();
        lblUnkShakeArgX = new javax.swing.JLabel();
        lblUnkShakeArgY = new javax.swing.JLabel();
        lblLoopCount = new javax.swing.JLabel();
        txtSequencing = new javax.swing.JTextField();
        txtDuration = new javax.swing.JTextField();
        txtShakeArgX = new javax.swing.JTextField();
        txtShakeArgY = new javax.swing.JTextField();
        txtUnkShakeArgX = new javax.swing.JTextField();
        txtUnkShakeArgY = new javax.swing.JTextField();
        txtLoopCount = new javax.swing.JTextField();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));
        lblHasCells = new javax.swing.JLabel();
        txtHasCells = new javax.swing.JTextField();
        menu = new javax.swing.JMenuBar();
        mnuFile = new javax.swing.JMenu();
        mniOpen = new javax.swing.JMenuItem();
        mniExit = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle(com.aurumsmods.tychogfx.TychoGfx.FULL_TITLE);
        setIconImage(TychoGfx.PROGRAM_ICON);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        toolbar.setFloatable(false);
        toolbar.setRollover(true);

        btnExportFrames.setIcon(new javax.swing.ImageIcon(getClass().getResource("/assets/img/tool_export24.png"))); // NOI18N
        btnExportFrames.setToolTipText("Export frame(s)");
        btnExportFrames.setEnabled(false);
        btnExportFrames.setFocusable(false);
        btnExportFrames.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExportFramesActionPerformed(evt);
            }
        });
        toolbar.add(btnExportFrames);
        toolbar.add(sep1);

        btnZoomOut.setIcon(new javax.swing.ImageIcon(getClass().getResource("/assets/img/tool_zoomout24.png"))); // NOI18N
        btnZoomOut.setToolTipText("Zoom out");
        btnZoomOut.setFocusable(false);
        btnZoomOut.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnZoomOut.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnZoomOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnZoomOutActionPerformed(evt);
            }
        });
        toolbar.add(btnZoomOut);

        btnZoomIn.setIcon(new javax.swing.ImageIcon(getClass().getResource("/assets/img/tool_zoomin24.png"))); // NOI18N
        btnZoomIn.setToolTipText("Zoom in");
        btnZoomIn.setFocusable(false);
        btnZoomIn.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnZoomIn.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnZoomIn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnZoomInActionPerformed(evt);
            }
        });
        toolbar.add(btnZoomIn);

        btnShowBoundingBox.setIcon(new javax.swing.ImageIcon(getClass().getResource("/assets/img/tool_show_bounding24.png"))); // NOI18N
        btnShowBoundingBox.setToolTipText("Show bounding box");
        btnShowBoundingBox.setFocusable(false);
        btnShowBoundingBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShowBoundingBoxActionPerformed(evt);
            }
        });
        toolbar.add(btnShowBoundingBox);
        toolbar.add(sep2);

        btnSetBackgroundColor.setIcon(new javax.swing.ImageIcon(getClass().getResource("/assets/img/tool_bgcolor.png"))); // NOI18N
        btnSetBackgroundColor.setToolTipText("Set background color");
        btnSetBackgroundColor.setFocusable(false);
        btnSetBackgroundColor.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnSetBackgroundColor.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnSetBackgroundColor.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetBackgroundColorActionPerformed(evt);
            }
        });
        toolbar.add(btnSetBackgroundColor);

        btnSetAxisColor.setIcon(new javax.swing.ImageIcon(getClass().getResource("/assets/img/tool_axiscolor.png"))); // NOI18N
        btnSetAxisColor.setToolTipText("Set axis color");
        btnSetAxisColor.setFocusable(false);
        btnSetAxisColor.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnSetAxisColor.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnSetAxisColor.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetAxisColorActionPerformed(evt);
            }
        });
        toolbar.add(btnSetAxisColor);
        toolbar.add(sep3);

        lblPalette.setText("Palette:");
        toolbar.add(lblPalette);

        sldPalette.setMajorTickSpacing(1);
        sldPalette.setMaximum(15);
        sldPalette.setPaintLabels(true);
        sldPalette.setPaintTicks(true);
        sldPalette.setSnapToTicks(true);
        sldPalette.setValue(0);
        sldPalette.setEnabled(false);
        sldPalette.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sldPaletteStateChanged(evt);
            }
        });
        toolbar.add(sldPalette);
        toolbar.add(filler2);

        splitOuter.setDividerLocation(650);
        splitOuter.setDividerSize(5);
        splitOuter.setResizeWeight(1.0);

        splitInner.setDividerLocation(180);
        splitInner.setDividerSize(5);

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("(nothing)");
        treeNodes.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        treeNodes.setCellRenderer(new ObjDescTreeCellRenderer());
        treeNodes.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
            public void valueChanged(javax.swing.event.TreeSelectionEvent evt) {
                treeNodesValueChanged(evt);
            }
        });
        scrollNodes.setViewportView(treeNodes);

        splitInner.setLeftComponent(scrollNodes);

        splitOuter.setLeftComponent(splitInner);

        pnlFrameInfo.setLayout(new java.awt.GridBagLayout());

        lblSequencing.setText("Sequencing");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(lblSequencing, gridBagConstraints);

        lblDuration.setText("Duration");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(lblDuration, gridBagConstraints);

        lblShakeArgX.setText("Shaking arg X");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(lblShakeArgX, gridBagConstraints);

        lblShakeArgY.setText("Shaking arg Y");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(lblShakeArgY, gridBagConstraints);

        lblUnkShakeArgX.setText("Unk. shaking arg X");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(lblUnkShakeArgX, gridBagConstraints);

        lblUnkShakeArgY.setText("Unk. shaking arg Y");
        lblUnkShakeArgY.setToolTipText("");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(lblUnkShakeArgY, gridBagConstraints);

        lblLoopCount.setText("Loop count");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(lblLoopCount, gridBagConstraints);

        txtSequencing.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.8;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(txtSequencing, gridBagConstraints);

        txtDuration.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.8;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(txtDuration, gridBagConstraints);

        txtShakeArgX.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.8;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(txtShakeArgX, gridBagConstraints);

        txtShakeArgY.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.8;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(txtShakeArgY, gridBagConstraints);

        txtUnkShakeArgX.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.8;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(txtUnkShakeArgX, gridBagConstraints);

        txtUnkShakeArgY.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.8;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(txtUnkShakeArgY, gridBagConstraints);

        txtLoopCount.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.8;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(txtLoopCount, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        pnlFrameInfo.add(filler1, gridBagConstraints);

        lblHasCells.setText("Has cells?");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(lblHasCells, gridBagConstraints);

        txtHasCells.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.8;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        pnlFrameInfo.add(txtHasCells, gridBagConstraints);

        splitOuter.setRightComponent(pnlFrameInfo);

        mnuFile.setText("File");

        mniOpen.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        mniOpen.setIcon(new javax.swing.ImageIcon(getClass().getResource("/assets/img/menu_open.png"))); // NOI18N
        mniOpen.setMnemonic('O');
        mniOpen.setText("Open");
        mniOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mniOpenActionPerformed(evt);
            }
        });
        mnuFile.add(mniOpen);

        mniExit.setText("Exit");
        mniExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mniExitActionPerformed(evt);
            }
        });
        mnuFile.add(mniExit);

        menu.add(mnuFile);

        setJMenuBar(menu);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(toolbar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(splitOuter, javax.swing.GroupLayout.DEFAULT_SIZE, 888, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(toolbar, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(splitOuter, javax.swing.GroupLayout.DEFAULT_SIZE, 420, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void mniOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mniOpenActionPerformed
        openObjDescFile();
    }//GEN-LAST:event_mniOpenActionPerformed

    private void mniExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mniExitActionPerformed
        System.exit(0);
    }//GEN-LAST:event_mniExitActionPerformed

    private void treeNodesValueChanged(javax.swing.event.TreeSelectionEvent evt) {//GEN-FIRST:event_treeNodesValueChanged
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode)treeNodes.getLastSelectedPathComponent();
        boolean isValidNodeSelected = selected != null;
        
        // Update sequencer's current animation and prerendered if a proper node is selected
        if (isValidNodeSelected && selected instanceof ObjDescEntryNode) {
            ObjDescEntryNode objDescNode = (ObjDescEntryNode)selected;
            String animType = objDescNode.animType;
            int animIndex = objDescNode.animIndex;
            int animFrameIndex = objDescNode.animFrameIndex;
            
            objDescSequencer.setSequenceAndFrame(objDesc, animType, animIndex, animFrameIndex);
            sequencerUpdateMode = ((ObjDescEntryNode)selected).sequencerUpdateMode;
        }
        // Otherwise, clear the sequencer context and disable rendering/updating
        else {
            objDescSequencer.clearContext();
            sequencerUpdateMode = UpdateMode.NO_RENDERING;
        }
        
        preview.repaint();
        updateCurrentFrameInfo();
        btnExportFrames.setEnabled(isValidNodeSelected && objDesc != null && objDesc.animationTypes().size() > 0);
    }//GEN-LAST:event_treeNodesValueChanged

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        updaterThread.start();
        updater.running.set(true);
    }//GEN-LAST:event_formWindowOpened

    private void btnExportFramesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnExportFramesActionPerformed
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode)treeNodes.getLastSelectedPathComponent();
        
        if (selected != null) {
            try {
                String folder = chooseDumpFolderPath();

                if (folder != null) {
                    boolean result = false;

                    if (selected instanceof ObjDescEntryNode) {
                        ObjDescEntryNode objDescNode = (ObjDescEntryNode)selected;
                        String animType = objDescNode.animType;
                        int animIndex = objDescNode.animIndex;
                        int animFrameIndex = objDescNode.animFrameIndex;

                        switch(objDescNode.dumpMode) {
                            case SEQUENCES -> result = ObjDescDumper.dumpAnimationSequences(objDesc, animType, folder);
                            case SEQUENCE -> result = ObjDescDumper.dumpAnimationSequence(objDesc, animType, animIndex, folder);
                            case FRAME -> result = ObjDescDumper.dumpAnimationFrame(objDesc, animType, animIndex, animFrameIndex, folder);
                        }
                    }
                    else
                        result = ObjDescDumper.dumpAnimationTypes(objDesc, folder);
                    
                    if (result)
                        JOptionPane.showMessageDialog(this, "Successfully dumped the animation frame(s).", TychoGfx.LONG_TITLE, JOptionPane.INFORMATION_MESSAGE);
                    else
                        JOptionPane.showMessageDialog(this, "Couldn't dump one or more frame(s).", TychoGfx.LONG_TITLE, JOptionPane.ERROR_MESSAGE);
                }
            }
            catch(IOException ex) {
                SwingUtil.showExceptionBox(this, ex, TychoGfx.LONG_TITLE);
            }
        }
    }//GEN-LAST:event_btnExportFramesActionPerformed

    private void btnShowBoundingBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShowBoundingBoxActionPerformed
        if (sequencerUpdateMode == UpdateMode.PAUSE)
            preview.repaint();
    }//GEN-LAST:event_btnShowBoundingBoxActionPerformed

    private void btnZoomOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnZoomOutActionPerformed
        tryZoom(-1);
    }//GEN-LAST:event_btnZoomOutActionPerformed

    private void btnZoomInActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnZoomInActionPerformed
        tryZoom(1);
    }//GEN-LAST:event_btnZoomInActionPerformed

    private void btnSetBackgroundColorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetBackgroundColorActionPerformed
        Color newcolor = JColorChooser.showDialog(this, "Select background color", backgroundColor, false);
        
        if (newcolor != null) {
            backgroundColor = newcolor;
            preview.repaint();
        }
    }//GEN-LAST:event_btnSetBackgroundColorActionPerformed

    private void btnSetAxisColorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetAxisColorActionPerformed
        Color newcolor = JColorChooser.showDialog(this, "Select axis color", axisColor, false);
        
        if (newcolor != null) {
            axisColor = newcolor;
            preview.repaint();
        }
    }//GEN-LAST:event_btnSetAxisColorActionPerformed

    private void sldPaletteStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_sldPaletteStateChanged
        if (objDesc != null) {
            objDesc.setPaletteOffsetAndPrerenderAllFrames(sldPalette.getValue());
            preview.repaint();
        }
    }//GEN-LAST:event_sldPaletteStateChanged
        
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnExportFrames;
    private javax.swing.JButton btnSetAxisColor;
    private javax.swing.JButton btnSetBackgroundColor;
    private javax.swing.JToggleButton btnShowBoundingBox;
    private javax.swing.JButton btnZoomIn;
    private javax.swing.JButton btnZoomOut;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JLabel lblDuration;
    private javax.swing.JLabel lblHasCells;
    private javax.swing.JLabel lblLoopCount;
    private javax.swing.JLabel lblPalette;
    private javax.swing.JLabel lblSequencing;
    private javax.swing.JLabel lblShakeArgX;
    private javax.swing.JLabel lblShakeArgY;
    private javax.swing.JLabel lblUnkShakeArgX;
    private javax.swing.JLabel lblUnkShakeArgY;
    private javax.swing.JMenuBar menu;
    private javax.swing.JMenuItem mniExit;
    private javax.swing.JMenuItem mniOpen;
    private javax.swing.JMenu mnuFile;
    private javax.swing.JPanel pnlFrameInfo;
    private javax.swing.JScrollPane scrollNodes;
    private javax.swing.JToolBar.Separator sep1;
    private javax.swing.JToolBar.Separator sep2;
    private javax.swing.JToolBar.Separator sep3;
    private javax.swing.JSlider sldPalette;
    private javax.swing.JSplitPane splitInner;
    private javax.swing.JSplitPane splitOuter;
    private javax.swing.JToolBar toolbar;
    private javax.swing.JTree treeNodes;
    private javax.swing.JTextField txtDuration;
    private javax.swing.JTextField txtHasCells;
    private javax.swing.JTextField txtLoopCount;
    private javax.swing.JTextField txtSequencing;
    private javax.swing.JTextField txtShakeArgX;
    private javax.swing.JTextField txtShakeArgY;
    private javax.swing.JTextField txtUnkShakeArgX;
    private javax.swing.JTextField txtUnkShakeArgY;
    // End of variables declaration//GEN-END:variables
}
