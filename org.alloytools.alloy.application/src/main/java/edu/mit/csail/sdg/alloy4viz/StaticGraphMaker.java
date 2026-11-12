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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JPanel;

import aQute.lib.justif.Justif;
import edu.mit.csail.sdg.alloy4.ErrorFatal;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.alloy4graph.CrossEdge;
import edu.mit.csail.sdg.alloy4graph.DiffMark;
import edu.mit.csail.sdg.alloy4graph.DotColor;
import edu.mit.csail.sdg.alloy4graph.DotDirection;
import edu.mit.csail.sdg.alloy4graph.DotPalette;
import edu.mit.csail.sdg.alloy4graph.DotShape;
import edu.mit.csail.sdg.alloy4graph.DotStyle;
import edu.mit.csail.sdg.alloy4graph.Graph;
import edu.mit.csail.sdg.alloy4graph.GraphEdge;
import edu.mit.csail.sdg.alloy4graph.GraphNode;
import edu.mit.csail.sdg.alloy4graph.GraphViewer;

/**
 * This utility class generates a graph for a particular index of the
 * projection.
 * <p>
 * <b>Thread Safety:</b> Can be called only by the AWT event thread.
 */

public final class StaticGraphMaker {

    /** The theme customization. */
    private final VizState                   view;

    /**
     * The projected instance for the graph currently being generated.
     */
    private final AlloyInstance              instance;

    /**
     * The projected model for the graph currently being generated.
     */
    private final AlloyModel                 model;

    /**
     * The map that contains all edges and what the AlloyTuple that each edge
     * corresponds to.
     */
    private final Map<GraphEdge,AlloyTuple>  edges     = new LinkedHashMap<GraphEdge,AlloyTuple>();

    /**
     * The map that contains all nodes and what the AlloyAtom that each node
     * corresponds to.
     */
    private final Map<GraphNode,AlloyAtom>   nodes     = new LinkedHashMap<GraphNode,AlloyAtom>();

    /**
     * This maps each atom to the nodes representing it; if an atom doesn't have a
     * node, it won't be in the map. An atom has one node, except in the graph of
     * all states of a trace, where it has one for each place it is ever in.
     */
    private final Map<AlloyAtom,List<GraphNode>> atom2nodes = new LinkedHashMap<AlloyAtom,List<GraphNode>>();

    /**
     * Maps to each node its path: the original names of the atoms it is inside of,
     * outermost first, and then its own, each preceded by a slash.
     */
    private final Map<String,GraphNode>          path2node  = new LinkedHashMap<String,GraphNode>();

    /** The edges between nodes of different containers, and the tuple of each. */
    private final Map<CrossEdge,AlloyTuple>      crosses    = new LinkedHashMap<CrossEdge,AlloyTuple>();

    /** Which atoms of the instance are drawn inside which. */
    private final Containment                    where;

    /**
     * If not null, the graph is that of all states of a trace: this maps the
     * original name of each atom to the original names of all the atoms it is ever
     * inside of, with "" for being inside none.
     */
    private final Map<String,Set<String>>        placements;

    /** Maps the original name of each atom of the instance to the atom. */
    private final Map<String,AlloyAtom>          name2atom  = new LinkedHashMap<String,AlloyAtom>();

    /**
     * This stores a set of additional labels we want to add to an existing node.
     */
    private final Map<GraphNode,Set<String>> attribs   = new LinkedHashMap<GraphNode,Set<String>>();

    /** The resulting graph. */
    private final Graph                      graph;


    /**
     * If not null, this maker adds the ghosts of a baseline instance to the graph
     * that the given maker made: nodes and edges for what the baseline has and the
     * shown instance lacks.
     */
    private final StaticGraphMaker           shown;

    /** If shown is not null, the differences from the baseline to the shown instance. */
    private final InstanceDiff               diff;

    /** Marks the nodes and edges that are new or different according to the given differences. */
    private void mark(InstanceDiff diff) {
        for (Map.Entry<GraphNode,AlloyAtom> e : nodes.entrySet()) {
            String name = e.getValue().getOriginalName();
            if (diff.addedAtoms().contains(name)) {
                e.getKey().set(DiffMark.ADDED);
                continue;
            }
            boolean changed = diff.movedAtoms().contains(name);
            for (AlloyRelation rel : model.getRelations())
                if (!changed && view.attribute.resolve(rel) && !(view.hidePrivate() && rel.isPrivate) && !(view.hideSkolem() && rel.isSkolem))
                    changed = diff.differsFrom(rel, name);
            if (changed)
                e.getKey().set(DiffMark.CHANGED);
        }
        for (Map.Entry<GraphEdge,AlloyTuple> e : edges.entrySet())
            if (e.getKey().group instanceof AlloyRelation && diff.isAdded((AlloyRelation) e.getKey().group, e.getValue()))
                e.getKey().set(DiffMark.ADDED);
        for (Map.Entry<CrossEdge,AlloyTuple> e : crosses.entrySet())
            if (e.getKey().group instanceof AlloyRelation && diff.isAdded((AlloyRelation) e.getKey().group, e.getValue()))
                e.getKey().set(DiffMark.ADDED);
    }

    /** Returns true if the theme lets the given atom of the given projected instance have a node. */
    static boolean canShow(VizState view, AlloyInstance instance, AlloyAtom atom) {
        final boolean hidePrivate = view.hidePrivate();
        if ((hidePrivate && atom.getType().isPrivate) || (view.hideMeta() && atom.getType().isMeta) || !view.nodeVisible(atom, instance))
            return false;
        for (AlloySet set : instance.atom2sets(atom))
            if (hidePrivate && set.isPrivate)
                return false;
        return true;
    }

    /** Works out which atoms of the given projected instance are drawn inside which. */
    static Containment containment(final VizState view, final AlloyInstance instance) {
        return new Containment(instance, view, new Predicate<AlloyAtom>() {

            @Override
            public boolean test(AlloyAtom atom) {
                return canShow(view, instance, atom);
            }
        });
    }

    /**
     * Builds the graph, not yet laid out, of everything that is in at least one of
     * the given states of a trace. An atom that is inside different atoms in
     * different states has a node in each of them, so that the layout of this graph
     * has a place for every node of the graph of every state.
     */
    public static Graph buildUnionGraph(List<AlloyInstance> states, VizState view, AlloyProjection proj) throws ErrorFatal {
        view = new VizState(view);
        if (proj == null)
            proj = new AlloyProjection();
        final Map<String,Set<String>> placements = new LinkedHashMap<String,Set<String>>();
        boolean any = false;
        for (AlloyInstance state : states) {
            AlloyInstance projected = StaticProjector.project(state, proj);
            Containment c = containment(view, projected);
            any |= !c.nested().isEmpty();
            for (AlloyAtom atom : projected.getAllAtoms()) {
                Set<String> set = placements.get(atom.getOriginalName());
                if (set == null)
                    placements.put(atom.getOriginalName(), set = new LinkedHashSet<String>());
                String outer = c.parentOf(atom.getOriginalName());
                set.add(outer == null ? "" : outer);
            }
        }
        Graph graph = new Graph(view.getFontSize() / 12.0D);
        new StaticGraphMaker(graph, TraceLayout.union(states), view, proj, null, null, any ? placements : null);
        return graph;
    }

    /**
     * Produces a single Graph from the given Instance and View and choice of
     * Projection
     */
    public static JPanel produceGraph(JFrame parent, AlloyInstance instance, VizState view, AlloyProjection proj) throws ErrorFatal {
        return produceGraph(parent, instance, view, proj, null);
    }

    /**
     * Produces a single Graph from the given Instance and View and choice of
     * Projection, laid out and marked as the given context asks (null if the graph
     * stands alone).
     */
    public static JPanel produceGraph(JFrame parent, AlloyInstance instance, VizState view, AlloyProjection proj, GraphContext context) throws ErrorFatal {
        if (context == null)
            return new GraphViewer(parent, buildGraph(instance, view, proj));
        return new GraphViewer(parent, buildGraph(instance, view, proj, context.baseline), context.template, context.strict);
    }

    /**
     * Builds the graph, not yet laid out, for the given Instance and View and
     * choice of Projection. Each node that stands for an atom has the atom's
     * original name as its layout key.
     */
    public static Graph buildGraph(AlloyInstance instance, VizState view, AlloyProjection proj) throws ErrorFatal {
        return buildGraph(instance, view, proj, null);
    }

    /**
     * Builds the graph, not yet laid out, for the given Instance and View and
     * choice of Projection, marked with its differences from the baseline instance
     * (if not null): what is new or different carries a DiffMark, and what is gone
     * is put back in as a ghost, marked REMOVED. Atoms are matched by their
     * original name.
     */
    public static Graph buildGraph(AlloyInstance instance, VizState view, AlloyProjection proj, AlloyInstance baseline) throws ErrorFatal {
        view = new VizState(view);
        if (proj == null)
            proj = new AlloyProjection();
        Graph graph = new Graph(view.getFontSize() / 12.0D);
        StaticGraphMaker now = new StaticGraphMaker(graph, instance, view, proj, null, null, null);
        if (baseline != null) {
            InstanceDiff diff = InstanceDiff.between(StaticProjector.project(baseline, proj), now.instance);
            now.mark(diff);
            new StaticGraphMaker(graph, baseline, view, proj, now, diff, null);
        }
        Set<AlloyType> distinctTypes = findNonIntAtoms(instance);
        if (distinctTypes.isEmpty()) {
            new GraphNode(graph, "", "No atoms found");
        } else if (graph.nodes.size() == 0)
            new GraphNode(graph, "", wrap("No visible atoms found, this can be caused by your theme settings.\nTheme settings might hide the following sigs that were found in the instance:\n" + distinctTypes));
        return graph;
    }

    private static String[] wrap(String description) {
        if (description == null)
            return new String[0];
        Justif justif = new Justif(40);
        StringBuilder sb = new StringBuilder(description);
        justif.wrap(sb);
        return sb.toString().split("\n");
    }

    private static Set<AlloyType> findNonIntAtoms(AlloyInstance instance) {
        return instance.getAllAtoms().stream().filter(x -> x.getType() != AlloyType.INT && x.getType() != AlloyType.SEQINT).map(x -> x.getType()).collect(Collectors.toSet());
    }

    /** The list of colors, in order, to assign each legend. */
    private static final List<Color> colorsClassic    = Util.asList(new Color(228, 26, 28), new Color(166, 86, 40), new Color(255, 127, 0), new Color(77, 175, 74), new Color(55, 126, 184), new Color(152, 78, 163));

    /** The list of colors, in order, to assign each legend. */
    private static final List<Color> colorsStandard   = Util.asList(new Color(227, 26, 28), new Color(255, 127, 0), new Color(251 * 8 / 10, 154 * 8 / 10, 153 * 8 / 10), new Color(51, 160, 44), new Color(31, 120, 180));

    /** The list of colors, in order, to assign each legend. */
    private static final List<Color> colorsMartha     = Util.asList(new Color(231, 138, 195), new Color(252, 141, 98), new Color(166, 216, 84), new Color(102, 194, 165), new Color(141, 160, 203));

    /** The list of colors, in order, to assign each legend. */
    private static final List<Color> colorsNeon       = Util.asList(new Color(231, 41, 138), new Color(217, 95, 2), new Color(166, 118, 29), new Color(102, 166, 30), new Color(27, 158, 119), new Color(117, 112, 179));

    /** The list of colors, in order, to assign each legend. */
    private static final List<Color> colorsColorBlind = Util.asList(new Color(119, 170, 221), new Color(153, 221, 255), new Color(187, 204, 51), new Color(170, 170, 0), new Color(238, 136, 102), new Color(255, 170, 187), new Color(221, 221, 221));

    private List<Color> getColors(DotPalette palette) {
        List<Color> colors;
        if (palette == DotPalette.CLASSIC)
            colors = colorsClassic;
        else if (palette == DotPalette.STANDARD)
            colors = colorsStandard;
        else if (palette == DotPalette.MARTHA)
            colors = colorsMartha;
        else if (palette == DotPalette.TOL_LIGHT)
            colors = colorsColorBlind;
        else
            colors = colorsNeon;
        return colors;
    }

    /**
     * The constructor takes an Instance and a View, then insert the generate
     * graph(s) into a blank cartoon.
     */
    private StaticGraphMaker(Graph graph, AlloyInstance originalInstance, VizState view, AlloyProjection proj, StaticGraphMaker shown, InstanceDiff diff, Map<String,Set<String>> placements) throws ErrorFatal {
        this.shown = shown;
        this.diff = diff;
        this.placements = placements;
        final boolean hidePrivate = view.hidePrivate();
        final boolean hideMeta = view.hideMeta();
        final boolean hideSkolem = view.hideSkolem();
        final Map<AlloyElement,Color> magicColor = new TreeMap<AlloyElement,Color>();
        final Map<AlloyRelation,Integer> rels = new TreeMap<AlloyRelation,Integer>();
        this.graph = graph;
        this.view = view;
        instance = StaticProjector.project(originalInstance, proj);
        model = instance.model;
        where = containment(view, instance);
        for (AlloyAtom atom : instance.getAllAtoms())
            name2atom.put(atom.getOriginalName(), atom);
        for (AlloyRelation rel : model.getRelations()) {
            rels.put(rel, null);
        }
        List<Color> colors = getColors(view.getEdgePalette());
        int ci = 0;
        for (AlloyRelation rel : model.getRelations()) {
            DotColor c = view.edgeColor.resolve(rel);
            Color cc = (c == DotColor.MAGIC) ? colors.get(ci) : c.getColor(view.getEdgePalette());
            int count = ((hidePrivate && rel.isPrivate) || (hideSkolem && rel.isSkolem) || !view.edgeVisible.resolve(rel)) ? 0 : edgesAsArcs(hidePrivate, hideMeta, hideSkolem, rel, colors.get(ci));
            if (Containment.applies(view, rel))
                count += edgesAsContainment(hidePrivate, hideMeta, hideSkolem, rel);
            rels.put(rel, count);
            magicColor.put(rel, cc);
            if (!(hidePrivate && rel.isPrivate) && !(hideSkolem && rel.isSkolem) && view.edgeVisible.resolve(rel) && model.getNonEmpty().contains(rel))
                ci = (ci + 1) % (colors.size());
        }
        ci = 0;
        colors = getColors(view.getNodePalette());
        for (AlloyType typ : model.getTypes()) {
            DotColor c = view.nodeColor.resolve(typ);
            Color cc = (c == DotColor.MAGIC) ? colors.get(ci) : c.getColor(view.getEdgePalette());
            magicColor.put(typ, cc);
            if (!(hidePrivate && typ.isPrivate) && view.nodeVisible.resolve(typ) && model.getNonEmpty().contains(typ))
                ci = (ci + 1) % (colors.size());
        }

        for (AlloyAtom atom : instance.getAllAtoms()) {
            List<AlloySet> sets = instance.atom2sets(atom);
            boolean any = false;
            for (AlloySet s : sets)
                if (!(hideSkolem && s.isSkolem)) {
                    any = true;
                    if (view.nodeVisible.resolve(s) && !view.hideUnconnected.resolve(s)) {
                        createNodes(hidePrivate, hideMeta, hideSkolem, atom, magicColor.get(atom.getType()));
                        break;
                    }
                }
            if (!any)
                if (view.nodeVisible.resolve(atom.getType()) && !view.hideUnconnected.resolve(atom.getType())) {
                    createNodes(hidePrivate, hideMeta, hideSkolem, atom, magicColor.get(atom.getType()));
                }
        }
        for (AlloyRelation rel : model.getRelations())
            if (!(hidePrivate && rel.isPrivate) && !(hideSkolem && rel.isSkolem))
                if (view.attribute.resolve(rel))
                    edgesAsAttribute(rel);
        for (Map.Entry<GraphNode,Set<String>> e : attribs.entrySet()) {
            Set<String> set = e.getValue();
            if (set != null)
                for (String s : set)
                    if (s.length() > 0)
                        e.getKey().addLabel(s);
        }
        if (shown != null)
            return; // the legend describes the instance that is shown
        for (Map.Entry<AlloyRelation,Integer> e : rels.entrySet()) {
            Color c = magicColor.get(e.getKey());
            if (c == null)
                c = Color.BLACK;
            int n = e.getValue();
            if (n > 0)
                graph.addLegend(e.getKey(), e.getKey().getName() + ": " + n, c);
            else
                graph.addLegend(e.getKey(), e.getKey().getName(), null);
        }
    }

    /**
     * Returns true if the given tuple is shown by putting one of its atoms inside
     * the other.
     */
    private boolean nests(AlloyRelation rel, AlloyTuple tuple) {
        if (placements == null)
            return where.nests(rel, tuple);
        if (!Containment.applies(view, rel) || tuple.getArity() != 2)
            return false;
        final boolean back = view.layoutBack.resolve(rel);
        final String outer = (back ? tuple.getEnd() : tuple.getStart()).getOriginalName(), inner = (back ? tuple.getStart() : tuple.getEnd()).getOriginalName();
        return placements.containsKey(inner) && placements.get(inner).contains(outer);
    }

    /** Makes sure that both atoms of every tuple that nests one atom in another have their nodes. */
    private int edgesAsContainment(final boolean hidePrivate, final boolean hideMeta, final boolean hideSkolem, AlloyRelation rel) {
        int count = 0;
        for (AlloyTuple tuple : instance.relation2tuples(rel))
            if (nests(rel, tuple) && !(shown != null && !diff.isRemoved(rel, tuple))) {
                createNodes(hidePrivate, hideMeta, hideSkolem, tuple.getStart(), null);
                createNodes(hidePrivate, hideMeta, hideSkolem, tuple.getEnd(), null);
                count++;
            }
        return count;
    }

    /**
     * Returns the paths of the nodes that the given atom is to have: one, unless
     * this is the graph of all states of a trace.
     *
     * @param below the names of the atoms being placed inside this one, which
     *            therefore cannot be around it
     */
    private List<String> paths(final boolean hidePrivate, final boolean hideMeta, final boolean hideSkolem, AlloyAtom atom, Set<String> below) {
        final String name = atom.getOriginalName();
        final List<String> ans = new ArrayList<String>();
        final List<AlloyAtom> outers = new ArrayList<AlloyAtom>();
        if (placements == null)
            outers.add(where.parentOf(atom));
        else if (placements.containsKey(name))
            for (String outer : placements.get(name))
                outers.add(name2atom.get(outer));
        final Set<String> inner = new HashSet<String>(below);
        inner.add(name);
        for (AlloyAtom outer : outers) {
            if (outer != null && !inner.contains(outer.getOriginalName()) && !createNodes(hidePrivate, hideMeta, hideSkolem, outer, null).isEmpty()) {
                for (String p : paths(hidePrivate, hideMeta, hideSkolem, outer, inner))
                    if (!ans.contains(p + "/" + name))
                        ans.add(p + "/" + name);
            } else if (!ans.contains("/" + name))
                ans.add("/" + name);
        }
        if (ans.isEmpty())
            ans.add("/" + name);
        return ans;
    }

    /** Returns the node at the given path in the graph, whichever maker made it; null if none. */
    private GraphNode at(String path) {
        GraphNode ans = path2node.get(path);
        return (ans == null && shown != null) ? shown.path2node.get(path) : ans;
    }

    /**
     * Returns the nodes for a specific AlloyAtom (creating them if they don't exist
     * yet).
     *
     * @return an empty list if the atom is explicitly marked as "Don't Show".
     */
    private List<GraphNode> createNodes(final boolean hidePrivate, final boolean hideMeta, final boolean hideSkolem, final AlloyAtom atom, Color magicColor) {
        List<GraphNode> made = atom2nodes.get(atom);
        if (made != null) {
            DotColor color = view.nodeColor(atom, instance);
            if (color == DotColor.MAGIC && magicColor != null && shown == null)
                for (GraphNode node : made)
                    node.set(magicColor);
            return made;
        }
        if (!canShow(view, instance, atom))
            return Collections.emptyList();
        made = new ArrayList<GraphNode>();
        atom2nodes.put(atom, made);
        for (String path : paths(hidePrivate, hideMeta, hideSkolem, atom, Collections.<String> emptySet())) {
            GraphNode node = at(path);
            if (node == null) {
                GraphNode outer = at(path.substring(0, path.lastIndexOf('/')));
                node = createNode(hidePrivate, hideMeta, hideSkolem, atom, magicColor, outer == null ? graph : outer.inside());
                path2node.put(path, node);
                // An atom that is still shown, but inside another atom, has moved there.
                final String name = atom.getOriginalName();
                final List<GraphNode> now = shown == null ? null : shown.atom2nodes.get(shown.name2atom.get(name));
                if (now != null && !Objects.equals(where.parentOf(name), shown.where.parentOf(name)))
                    for (GraphNode there : now)
                        there.set(DiffMark.ADDED);
            }
            made.add(node);
        }
        return made;
    }

    /** Makes a node for the given atom in the given graph. */
    private GraphNode createNode(final boolean hidePrivate, final boolean hideMeta, final boolean hideSkolem, final AlloyAtom atom, Color magicColor, Graph graph) {
        GraphNode node;
        // Make the node
        DotColor color = view.nodeColor(atom, instance);
        DotStyle style = view.nodeStyle(atom, instance);
        DotShape shape = view.shape(atom, instance);
        String label = atomname(atom, false);

        node = new GraphNode(graph, atom, label).set(shape).set(style).setLayoutKey(atom.getOriginalName());
        if (color == DotColor.MAGIC && magicColor != null)
            node.set(magicColor);
        else
            node.set(color.getColor(view.getNodePalette()));

        // Get the label based on the sets and relations
        String setsLabel = "";
        boolean showLabelByDefault = view.showAsLabel.get(null);
        for (AlloySet set : instance.atom2sets(atom)) {
            String x = view.label.get(set);
            if (x.length() == 0 || (hideSkolem && set.isSkolem))
                continue;
            Boolean showLabel = view.showAsLabel.get(set);
            if ((showLabel == null && showLabelByDefault) || (showLabel != null && showLabel.booleanValue()))
                setsLabel += ((setsLabel.length() > 0 ? ", " : "") + x);
        }
        if (setsLabel.length() > 0) {
            Set<String> list = attribs.get(node);
            if (list == null)
                attribs.put(node, list = new TreeSet<String>());
            list.add("(" + setsLabel + ")");
        }
        if (shown != null)
            node.set(DiffMark.REMOVED);
        nodes.put(node, atom);
        return node;
    }

    /**
     * Create an edge for a given tuple from a relation (if neither start nor end
     * node is explicitly invisible)
     */
    private boolean createEdge(final boolean hidePrivate, final boolean hideMeta, final boolean hideSkolem, AlloyRelation rel, AlloyTuple tuple, boolean bidirectional, Color magicColor) {
        // This edge represents a given tuple from a given relation.
        //
        // If the tuple's arity==2, then the label is simply the label of the
        // relation.
        //
        // If the tuple's arity>2, then we append the node labels for all the
        // intermediate nodes.
        // eg. Say a given tuple is (A,B,C,D) from the relation R.
        // An edge will be drawn from A to D, with the label "R [B, C]"
        if (shown != null && !diff.isRemoved(rel, tuple))
            return false;
        if ((hidePrivate && tuple.getStart().getType().isPrivate) || (hideMeta && tuple.getStart().getType().isMeta) || !view.nodeVisible(tuple.getStart(), instance))
            return false;
        if ((hidePrivate && tuple.getEnd().getType().isPrivate) || (hideMeta && tuple.getEnd().getType().isMeta) || !view.nodeVisible(tuple.getEnd(), instance))
            return false;
        if (nests(rel, tuple))
            return false;
        List<GraphNode> starts = createNodes(hidePrivate, hideMeta, hideSkolem, tuple.getStart(), null);
        List<GraphNode> ends = createNodes(hidePrivate, hideMeta, hideSkolem, tuple.getEnd(), null);
        if (starts.isEmpty() || ends.isEmpty())
            return false;
        boolean layoutBack = view.layoutBack.resolve(rel);
        String label = view.label.get(rel);
        if (tuple.getArity() > 2) {
            StringBuilder moreLabel = new StringBuilder();
            List<AlloyAtom> atoms = tuple.getAtoms();
            for (int i = 1; i < atoms.size() - 1; i++) {
                if (i > 1)
                    moreLabel.append(", ");
                moreLabel.append(atomname(atoms.get(i), false));
            }
            if (label.length() == 0) {
                /* label=moreLabel.toString(); */ } else {
                label = label + (" [" + moreLabel + "]");
            }
        }
        DotDirection dir = bidirectional ? DotDirection.BOTH : (layoutBack ? DotDirection.BACK : DotDirection.FORWARD);
        DotStyle style = view.edgeStyle.resolve(rel);
        DotColor color = view.edgeColor.resolve(rel);
        int weight = view.weight.get(rel);
        final Color paint = (color == DotColor.MAGIC && magicColor != null) ? magicColor : color.getColor(view.getEdgePalette());
        final int pull = weight < 1 ? 1 : (weight > 100 ? 10000 : 100 * weight);
        for (GraphNode start : starts)
            for (GraphNode end : ends) {
                final GraphNode from = layoutBack ? end : start, to = layoutBack ? start : end;
                if (from.container() == to.container()) {
                    GraphEdge e = new GraphEdge(from, to, tuple, label, rel);
                    e.set(paint).set(style).set(dir != DotDirection.FORWARD, dir != DotDirection.BACK).set(pull);
                    if (shown != null)
                        e.set(DiffMark.REMOVED).set(1); // a ghost gives way to the edges that are there
                    edges.put(e, tuple);
                } else {
                    CrossEdge e = graph.connect(from, to, tuple, label, rel);
                    e.set(paint).set(style).set(dir != DotDirection.FORWARD, dir != DotDirection.BACK).set(pull);
                    if (shown != null)
                        e.set(DiffMark.REMOVED).set(1);
                    crosses.put(e, tuple);
                }
            }
        return true;
    }

    /**
     * Create edges for every visible tuple in the given relation.
     */
    private int edgesAsArcs(final boolean hidePrivate, final boolean hideMeta, final boolean hideSkolem, AlloyRelation rel, Color magicColor) {
        int count = 0;
        if (!view.mergeArrows.resolve(rel)) {
            // If we're not merging bidirectional arrows, simply create an edge
            // for each tuple.
            for (AlloyTuple tuple : instance.relation2tuples(rel))
                if (createEdge(hidePrivate, hideMeta, hideSkolem, rel, tuple, false, magicColor))
                    count++;
            return count;
        }
        // Otherwise, find bidirectional arrows and only create one edge for
        // each pair.
        Set<AlloyTuple> tuples = instance.relation2tuples(rel);
        Set<AlloyTuple> ignore = new LinkedHashSet<AlloyTuple>();
        for (AlloyTuple tuple : tuples) {
            if (!ignore.contains(tuple)) {
                AlloyTuple reverse = tuple.getArity() > 2 ? null : tuple.reverse();
                // If the reverse tuple is in the same relation, and it is not a
                // self-edge, then draw it as a <-> arrow.
                if (reverse != null && tuples.contains(reverse) && !reverse.equals(tuple)) {
                    ignore.add(reverse);
                    if (createEdge(hidePrivate, hideMeta, hideSkolem, rel, tuple, true, magicColor))
                        count = count + 2;
                } else {
                    if (createEdge(hidePrivate, hideMeta, hideSkolem, rel, tuple, false, magicColor))
                        count = count + 1;
                }
            }
        }
        return count;
    }

    /** Attach tuple values as attributes to existing nodes. */
    private void edgesAsAttribute(AlloyRelation rel) {
        // If this relation wants to be shown as an attribute,
        // then generate the annotations and attach them to each tuple's
        // starting node.
        // Eg.
        // If (A,B) and (A,C) are both in the relation F,
        // then the A node would have a line that says "F: B, C"
        // Eg.
        // If (A,B,C) and (A,D,E) are both in the relation F,
        // then the A node would have a line that says "F: B->C, D->E"
        // Eg.
        // If (A,B,C) and (A,D,E) are both in the relation F, and B belongs to
        // sets SET1 and SET2,
        // and SET1's "show in relational attribute" is on,
        // and SET2's "show in relational attribute" is on,
        // then the A node would have a line that says "F: B (SET1, SET2)->C,
        // D->E"
        //
        Map<GraphNode,String> map = new LinkedHashMap<GraphNode,String>();
        for (AlloyTuple tuple : instance.relation2tuples(rel)) {
            List<GraphNode> starts = atom2nodes.get(tuple.getStart());
            if (starts == null || starts.isEmpty())
                continue; // this means the node won't be shown, so we can't
                         // show any attributes
            String attr = "";
            List<AlloyAtom> atoms = tuple.getAtoms();
            for (int i = 1; i < atoms.size(); i++) {
                if (i > 1)
                    attr += "->";
                attr += atomname(atoms.get(i), true);
            }
            if (attr.length() == 0)
                continue;
            for (GraphNode start : starts) {
                String oldattr = map.get(start), all = attr;
                if (oldattr != null && oldattr.length() > 0)
                    all = oldattr + ", " + all;
                if (all.length() > 0)
                    map.put(start, all);
            }
        }
        for (Map.Entry<GraphNode,String> e : map.entrySet()) {
            GraphNode node = e.getKey();
            Set<String> list = attribs.get(node);
            if (list == null)
                attribs.put(node, list = new TreeSet<String>());
            String attr = e.getValue();
            if (view.label.get(rel).length() > 0)
                attr = view.label.get(rel) + ": " + attr;
            list.add(attr);
        }
    }

    /**
     * Return the label for an atom.
     *
     * @param atom - the atom
     * @param showSets - whether the label should also show the sets that this atom
     *            belongs to
     *            <p>
     *            eg. If atom A is the 3rd atom in type T, and T's label is
     *            "Person", then the return value would be "Person3".
     *            <p>
     *            eg. If atom A is the only atom in type T, and T's label is
     *            "Person", then the return value would be "Person".
     *            <p>
     *            eg. If atom A is the 3rd atom in type T, and T's label is
     *            "Person", and T belongs to the sets Set1, Set2, and Set3. However,
     *            only Set1 and Set2 have "show in relational attribute == on", then
     *            the return value would be "Person (Set1, Set2)".
     */
    private String atomname(AlloyAtom atom, boolean showSets) {
        String label = atom.getVizName(view, view.number.resolve(atom.getType()));
        if (!showSets)
            return label;
        String attr = "";
        boolean showInAttrByDefault = view.showAsAttr.get(null);
        for (AlloySet set : instance.atom2sets(atom)) {
            String x = view.label.get(set);
            if (x.length() == 0)
                continue;
            Boolean showAsAttr = view.showAsAttr.get(set);
            if ((showAsAttr == null && showInAttrByDefault) || (showAsAttr != null && showAsAttr))
                attr += ((attr.length() > 0 ? ", " : "") + x);
        }
        if (label.length() == 0)
            return (attr.length() > 0) ? ("(" + attr + ")") : "";
        return (attr.length() > 0) ? (label + " (" + attr + ")") : label;
    }

    static String esc(String name) {
        if (name.indexOf('\"') < 0)
            return name;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\"')
                out.append('\\');
            out.append(c);
        }
        return out.toString();
    }
}
