/* Alloy Analyzer 4 -- Copyright (c) 2006-2009, Felix Chang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package edu.mit.csail.sdg.alloy4viz;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import edu.mit.csail.sdg.alloy4.ConstList;
import edu.mit.csail.sdg.alloy4.OurBorder;
import edu.mit.csail.sdg.alloy4.OurCombobox;
import edu.mit.csail.sdg.alloy4.OurUtil;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.alloy4graph.GraphViewer;

/**
 * GUI panel that houses the actual graph, as well as any projection comboboxes.
 * <p>
 * <b>Thread Safety:</b> Can be called only by the AWT event thread.
 *
 * @modified [electrum] apply default style for mutable elements; a graph panel
 *           now holds a list of graphs (and associated components), each with
 *           its own viz state; assumes that cannot project over mutable
 *           variables;
 */

public final class VizGraphPanel extends JPanel {

    /** This ensures the class can be serialized reliably. */
    private static final long              serialVersionUID    = 0;

    /** This is the current customization settings of each graph panel. */
    private final List<VizState>           vizState;

    /**
     * Whether the user wants to see the DOT source code or not.
     */
    private boolean                        seeDot              = false;

    /**
     * The current GraphViewer (or null if we are not looking at a GraphViewer).
     */
    private List<GraphViewer>              viewer              = null;

    /**
     * The scrollpanes containing the upperhalf of the panel (showing the graphs).
     */
    private final List<JScrollPane>        diagramScrollPanels = new ArrayList<JScrollPane>();

    /** The upperhalf of the panel (showing the graphs). */
    private final List<JPanel>             graphPanels         = new ArrayList<JPanel>();

    /**
     * The lowerhalf of the panel (showing the comboboxes for choosing the projected
     * atoms).
     */
    private final JPanel                   navPanel;

    /**
     * The splitpane separating the graphPanel and the navPanel.
     */
    private final JSplitPane               split;

    /**
     * The current projection choice; null if no projection is in effect.
     */
    private AlloyProjection                currentProjection   = null;

    /**
     * This is the list of TypePanel(s) we've already constructed.
     */
    private final Map<AlloyType,TypePanel> type2panel          = new TreeMap<AlloyType,TypePanel>();

    /**
     * Inner class that displays a combo box of possible projection atom choices.
     */
    final class TypePanel extends JPanel {

        /** This ensures the class can be serialized reliably. */
        private static final long     serialVersionUID = 0;
        /** The type being projected. */
        private final AlloyType       type;
        /**
         * The list of atoms; can be an empty list if there are no atoms in this type to
         * be projected.
         */
        private final List<AlloyAtom> atoms;
        /**
         * The list of atom names; atomnames.empty() iff atoms.isEmpty()
         */
        private final String[]        atomnames;
        /**
         * The combo box showing the possible atoms to choose from.
         */
        private final JComboBox       atomCombo;

        /**
         * True if this TypePanel object does not need to be rebuilt.
         */
        private boolean upToDate(AlloyType type, List<AlloyAtom> atoms) {
            if (!this.type.equals(type))
                return false;
            atoms = new ArrayList<AlloyAtom>(atoms);
            Collections.sort(atoms);
            if (!this.atoms.equals(atoms))
                return false;
            for (int i = 0; i < this.atoms.size(); i++) {
                String n = this.atoms.get(i).getVizName(vizState.get(0), true);
                if (!atomnames[i].equals(n))
                    return false;
            }
            return true;
        }

        /**
         * Constructs a new TypePanel.
         *
         * @param type - the type being projected
         * @param atoms - the list of possible projection atom choices
         */
        private TypePanel(JFrame parent, AlloyType type, List<AlloyAtom> atoms, AlloyAtom initialValue) {
            super();
            final JButton left, right;
            int initialIndex = 0;
            this.type = type;
            atoms = new ArrayList<AlloyAtom>(atoms);
            Collections.sort(atoms);
            this.atoms = ConstList.make(atoms);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setBorder(null);
            this.atomnames = new String[this.atoms.size()];
            for (int i = 0; i < this.atoms.size(); i++) {
                atomnames[i] = this.atoms.get(i).getVizName(vizState.get(0), true);
                if (this.atoms.get(i).equals(initialValue))
                    initialIndex = i;
            }
            add(left = new JButton("<<"));
            add(Box.createRigidArea(new Dimension(2, 2)));
            add(atomCombo = new OurCombobox(atomnames.length < 1 ? new String[] {
                                                                                 " "
            } : atomnames));
            add(Box.createRigidArea(new Dimension(2, 2)));
            add(right = new JButton(">>"));
            left.setVerticalAlignment(SwingConstants.CENTER);
            right.setVerticalAlignment(SwingConstants.CENTER);
            Dimension dim = atomCombo.getPreferredSize();
            int idealWidth = Util.onMac() ? 120 : 80;
            if (dim.width < idealWidth) {
                dim.width = idealWidth + 20;
                atomCombo.setMinimumSize(dim);
                atomCombo.setPreferredSize(dim);
            }
            left.setEnabled(initialIndex > 0);
            right.setEnabled(initialIndex < atomnames.length - 1);
            atomCombo.setSelectedIndex(initialIndex);
            if (Util.onMac())
                atomCombo.setBorder(BorderFactory.createEmptyBorder(4, 1, 0, 1));
            left.addActionListener(new ActionListener() {

                @Override
                public final void actionPerformed(ActionEvent e) {
                    int curIndex = atomCombo.getSelectedIndex();
                    if (curIndex > 0)
                        atomCombo.setSelectedIndex(curIndex - 1);
                }
            });
            right.addActionListener(new ActionListener() {

                @Override
                public final void actionPerformed(ActionEvent e) {
                    int curIndex = atomCombo.getSelectedIndex();
                    if (curIndex < atomCombo.getItemCount() - 1)
                        atomCombo.setSelectedIndex(curIndex + 1);
                }
            });
            atomCombo.addActionListener(new ActionListener() {

                @Override
                public final void actionPerformed(ActionEvent e) {
                    left.setEnabled(atomCombo.getSelectedIndex() > 0);
                    right.setEnabled(atomCombo.getSelectedIndex() < atomnames.length - 1);
                    remakeAll(parent);
                    VizGraphPanel.this.getParent().invalidate();
                    VizGraphPanel.this.getParent().repaint();
                }
            });
        }

        /**
         * Returns the entire list of atoms; could be an empty set.
         */
        public List<AlloyAtom> getAlloyAtoms() {
            return atoms;
        }

        /**
         * Returns the currently-selected atom; returns null if the list is empty.
         */
        public AlloyAtom getAlloyAtom() {
            int i = atomCombo.getSelectedIndex();
            if (i >= 0 && i < atoms.size())
                return atoms.get(i);
            else
                return null;
        }

        /** Returns the AlloyType associated with this TypePanel. */
        public AlloyType getAlloyType() {
            return type;
        }
    }

    /**
     * Create a splitpane showing the graphs on top, as well as projection
     * comboboxes on the bottom.
     *
     * @param vizState - the current visualization settings
     * @param seeDot - true if we want to see the DOT source code, false if we want
     *            it rendered as a graph
     */
    public VizGraphPanel(JFrame parent, List<VizState> vizState, boolean seeDot) {
        this(parent, vizState, seeDot, null, 0);
    }

    /**
     * Create a splitpane showing the graphs on top, as well as projection
     * comboboxes on the bottom.
     *
     * @param vizState - the current visualization settings
     * @param seeDot - true if we want to see the DOT source code, false if we want
     *            it rendered as a graph
     * @param continuity - what keeps the graphs consistent with each other (null if
     *            nothing does)
     * @param firstState - the index of the state shown by the first graph
     */
    public VizGraphPanel(JFrame parent, List<VizState> vizState, boolean seeDot, Continuity continuity, int firstState) {
        this(parent, vizState, seeDot, continuity, firstState, null);
    }

    /**
     * Create a splitpane showing the graphs on top, as well as projection
     * comboboxes on the bottom.
     *
     * @param vizState - the current visualization settings
     * @param seeDot - true if we want to see the DOT source code, false if we want
     *            it rendered as a graph
     * @param continuity - what keeps the graphs consistent with each other (null if
     *            nothing does)
     * @param firstState - the index of the state shown by the first graph
     * @param onSelectFrame - if not null, the graphs are shown as a storyboard: a
     *            strip of frames at one common scale, each under a heading that
     *            names its state; this is called with the index of the state whose
     *            heading the user clicks
     */
    public VizGraphPanel(JFrame parent, List<VizState> vizState, boolean seeDot, Continuity continuity, int firstState, IntConsumer onSelectFrame) {
        this.continuity = continuity;
        this.firstState = firstState;
        this.onSelectFrame = onSelectFrame;
        Border b = new EmptyBorder(0, 0, 0, 0);
        OurUtil.make(this, Color.BLACK, Color.WHITE, b);
        this.seeDot = seeDot;
        this.vizState = vizState;
        setLayout(new GridLayout());
        setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
        navPanel = new JPanel();
        JScrollPane navscroll = OurUtil.scrollpane(navPanel);
        navscroll.setMinimumSize(new Dimension(0, 0));
        // [electrum] container for all (diagram scroll) graph panels
        JPanel diagramsScrollPanels = new JPanel();
        diagramsScrollPanels.setLayout(new BoxLayout(diagramsScrollPanels, BoxLayout.LINE_AXIS));
        for (int i = 0; i < vizState.size(); i++) {
            JScrollPane diagramScrollPanel = createGraphPanel(i);
            diagramScrollPanels.add(diagramScrollPanel);
            if (onSelectFrame == null) {
                diagramsScrollPanels.add(diagramScrollPanel);
                diagramScrollPanel.setPreferredSize(new Dimension(0, 0));
            } else
                diagramsScrollPanels.add(createFrame(i, diagramScrollPanel));
        }
        JComponent diagrams = diagramsScrollPanels;
        if (onSelectFrame != null) {
            // The frames keep their own size, and the strip as a whole scrolls.
            diagramsScrollPanels.setBackground(Color.WHITE);
            diagramsScrollPanels.add(Box.createHorizontalGlue());
            diagrams = OurUtil.scrollpane(diagramsScrollPanels, new OurBorder(true, false, true, false));
        }

        split = OurUtil.splitpane(JSplitPane.VERTICAL_SPLIT, diagrams, navscroll, 0);
        split.setResizeWeight(1.0);
        split.setDividerSize(0);
        add(split);
        remakeAll(parent);
    }

    /**
     * Creates a particular diagram scroll panel.
     *
     * @param i the i-th panel in the visualizer
     * @return the i-th diagram graph panel
     */
    // [electrum] refactored from VizGraphPanel constructor so that multiple can be created
    private JScrollPane createGraphPanel(int i) {
        Border b = new EmptyBorder(0, 0, 0, 0);

        JPanel graphPanel = OurUtil.make(new JPanel(), Color.BLACK, Color.WHITE, b);
        graphPanels.add(graphPanel);
        graphPanel.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent ev) {
                // We let Ctrl+LeftClick bring up the popup menu, just like RightClick,
                // since many Mac mouses do not have a right button.
                if (viewer.size() <= i)
                    return;
                else if (ev.getButton() == MouseEvent.BUTTON3) {
                } else if (ev.getButton() == MouseEvent.BUTTON1 && ev.isControlDown()) {} else
                    return;
                if (graphPanel.contains(ev.getX(), ev.getY())) // [electrum] distinguish clicked panel
                    viewer.get(i).alloyPopup(graphPanel, ev.getX(), ev.getY());
            }
        });

        JScrollPane diagramScrollPanel = OurUtil.scrollpane(graphPanel, new OurBorder(true, true, true, false));
        diagramScrollPanel.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {

            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                diagramScrollPanel.invalidate();
                diagramScrollPanel.repaint();
                diagramScrollPanel.validate();
            }
        });
        diagramScrollPanel.getHorizontalScrollBar().addAdjustmentListener(new AdjustmentListener() {

            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                diagramScrollPanel.invalidate();
                diagramScrollPanel.repaint();
                diagramScrollPanel.validate();
            }
        });
        return diagramScrollPanel;
    }

    /** What keeps the graphs consistent with each other; null if nothing does. */
    private final Continuity continuity;

    /** The index of the state shown by the first graph. */
    private int              firstState;

    /** Sets the index of the state shown by the first graph; takes effect at the next remakeAll. */
    public void setFirstState(int firstState) {
        this.firstState = firstState;
    }

    /** Returns the projection currently shown. */
    public AlloyProjection currentProjection() {
        return currentProjection;
    }

    /** Returns the graph of the i-th panel under the current projection. */
    private JPanel graphOf(JFrame parent, int i) {
        GraphContext context = continuity == null ? null : continuity.contextFor(firstState + i, vizState.get(0), currentProjection);
        return vizState.get(i).getGraph(parent, currentProjection, context);
    }

    /**
     * If not null, the graphs are shown as a storyboard, and this is called with
     * the index of the state whose heading the user clicks.
     */
    private final IntConsumer  onSelectFrame;

    /** In a storyboard, the heading of each frame. */
    private final List<JLabel> headings      = new ArrayList<JLabel>();

    /** In a storyboard, the index of the state whose frame is marked as selected. */
    private int                selectedState = -1;

    /** The smallest scale that a storyboard shrinks its frames to before it scrolls instead. */
    private static final double minStoryboardScale = 0.6;

    /** Returns true if the graphs are shown as a storyboard. */
    public boolean isStoryboard() {
        return onSelectFrame != null;
    }

    /**
     * Sets the index of the state whose frame a storyboard marks as selected; takes
     * effect at the next remakeAll.
     */
    public void setSelectedState(int selectedState) {
        this.selectedState = selectedState;
    }

    /**
     * Creates the i-th frame of a storyboard: the given graph pane, which takes the
     * size of its graph, under a heading.
     */
    private JPanel createFrame(final int i, JScrollPane graphPane) {
        graphPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        graphPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        graphPane.setBorder(null);
        JLabel heading = new JLabel(" ");
        heading.setOpaque(true);
        heading.setBorder(new EmptyBorder(3, 8, 3, 8));
        heading.setToolTipText("Click to select this state");
        heading.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent ev) {
                if (ev.getButton() == MouseEvent.BUTTON1)
                    onSelectFrame.accept(firstState + i);
            }
        });
        headings.add(heading);
        JPanel frame = new JPanel(new BorderLayout());
        frame.setBackground(Color.WHITE);
        frame.setBorder(new OurBorder(false, false, false, true));
        frame.add(heading, BorderLayout.NORTH);
        frame.add(graphPane, BorderLayout.CENTER);
        frame.setAlignmentY(0f);
        return frame;
    }

    /**
     * In a storyboard, names each frame and gives all graphs one scale: the one at
     * which the whole strip fits the window, down to a readable minimum.
     */
    private void arrangeStoryboard(JFrame parent) {
        for (int i = 0; i < headings.size(); i++) {
            boolean selected = firstState + i == selectedState;
            JLabel heading = headings.get(i);
            heading.setText(continuity == null ? "State " + (firstState + i) : continuity.title(firstState + i));
            heading.setFont(heading.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
            heading.setBackground(selected ? new Color(200, 225, 255) : new Color(238, 238, 238));
        }
        if (viewer == null || viewer.isEmpty())
            return;
        int widest = 1;
        for (GraphViewer v : viewer)
            widest = Math.max(widest, v.getGraph().getTotalWidth());
        double natural = vizState.get(0).getFontSize() / 12.0D;
        double room = ((parent != null && parent.getWidth() > 0 ? parent.getWidth() : 1200) - 40) / (double) viewer.size() - 12;
        double scale = Math.max(minStoryboardScale * natural, Math.min(natural, room / widest));
        for (GraphViewer v : viewer)
            v.setScale(scale);
    }

    /** Makes hovering over an atom in one graph highlight the same atom in the others. */
    private void linkHovering() {
        if (viewer == null || viewer.size() < 2)
            return;
        final List<GraphViewer> all = new ArrayList<GraphViewer>(viewer);
        for (final GraphViewer source : all)
            source.setHoverListener(key -> {
                for (GraphViewer other : all)
                    if (other != source)
                        other.highlightKey(key);
            });
    }

    /** Regenerate the comboboxes and the graph. */
    public void remakeAll(JFrame parent) {
        Map<AlloyType,AlloyAtom> map = new LinkedHashMap<AlloyType,AlloyAtom>();
        navPanel.removeAll();
        // [electrum] this info is the same in all viz states
        for (AlloyType type : vizState.get(vizState.size() - 1).getProjectedTypes()) {
            List<AlloyAtom> atoms = vizState.get(vizState.size() - 1).getOriginalInstance().type2atoms(type);
            TypePanel tp = type2panel.get(type);
            if (tp != null && tp.getAlloyAtom() != null && !atoms.contains(tp.getAlloyAtom()))
                tp = null;
            if (tp != null && tp.getAlloyAtom() == null && atoms.size() > 0)
                tp = null;
            if (tp != null && !tp.upToDate(type, atoms))
                tp = null;
            if (tp == null)
                type2panel.put(type, tp = new TypePanel(parent, type, atoms, null));
            navPanel.add(tp);
            map.put(tp.getAlloyType(), tp.getAlloyAtom());
        }
        currentProjection = new AlloyProjection(map);
        List<GraphViewer> prevsv = viewer;
        // [electrum] update all graph panels
        viewer = new ArrayList<>(vizState.size());
        for (int i = 0; i < vizState.size(); i++) {
            JPanel graph = graphOf(parent, i);
            if (seeDot && (graph instanceof GraphViewer)) {
                viewer = null;
                JTextArea txt = OurUtil.textarea(graph.toString(), 10, 10, false, true, getFont());
                diagramScrollPanels.get(i).setViewportView(txt);
            } else {
                if (graph instanceof GraphViewer) {
                    viewer.add((GraphViewer) graph);
                    if (!isStoryboard() && prevsv != null && i < prevsv.size() && prevsv.get(i) != graph)
                        ((GraphViewer) graph).setScale(prevsv.get(i).getScale());
                } else
                    viewer = null;
                graphPanels.get(i).removeAll();
                graphPanels.get(i).add(graph);
                graphPanels.get(i).setBackground(Color.WHITE);
                diagramScrollPanels.get(i).setViewportView(graphPanels.get(i));
                diagramScrollPanels.get(i).invalidate();
                diagramScrollPanels.get(i).repaint();
                diagramScrollPanels.get(i).validate();
            }
        }
        linkHovering();
        if (isStoryboard()) {
            arrangeStoryboard(parent);
            for (int i = 0; i < graphPanels.size(); i++) {
                diagramScrollPanels.get(i).setPreferredSize(null);
                graphPanels.get(i).revalidate();
            }
        }
        split.setDividerLocation(split.getSize().height - split.getInsets().bottom - split.getDividerSize() - split.getRightComponent().getPreferredSize().height);
    }

    /** Changes the font. */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
        if (diagramScrollPanels != null) // [electrum] called before initialization
            for (JScrollPane diagramScrollPanel : diagramScrollPanels)
                diagramScrollPanel.getViewport().getView().setFont(font);
    }

    /** Changes whether we are seeing the DOT source or not. */
    public void seeDot(JFrame parent, boolean yesOrNo) {
        if (seeDot == yesOrNo)
            return;
        seeDot = yesOrNo;
        remakeAll(parent);
    }

    public String toDot(JFrame parent) {
        // [electrum] only converts the first shown state
        return graphOf(parent, 0).toString();
    }

    /**
     * Retrieves the actual GraphViewer object that contains the graph (or null if
     * the graph hasn't loaded yet)
     */
    public GraphViewer alloyGetViewer() {
        if (viewer == null || viewer.isEmpty())
            return null;
        return viewer.get(0);
    }

    public void resetProjectionAtomCombos() {
        for (Entry<AlloyType,TypePanel> e : type2panel.entrySet()) {
            if (e.getValue().atomCombo != null)
                e.getValue().atomCombo.setSelectedIndex(0);
        }
    }

    /**
     * The number of graph panels in the viz.
     *
     * @return the number of graph panels
     */
    public int numPanels() {
        return graphPanels.size();
    }

}
