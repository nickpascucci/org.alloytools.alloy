package edu.mit.csail.sdg.alloy4viz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import edu.mit.csail.sdg.alloy4graph.CrossEdge;
import edu.mit.csail.sdg.alloy4graph.DiffMark;
import edu.mit.csail.sdg.alloy4graph.Graph;
import edu.mit.csail.sdg.alloy4graph.GraphEdge;
import edu.mit.csail.sdg.alloy4graph.GraphNode;
import edu.mit.csail.sdg.alloy4graph.LayoutTemplate;

public class ContainerGraphTest {

    /** A directory tree three levels deep, with a link between files of different directories. */
    static final String TREE  = String.join("\n", "abstract sig Object {}", "sig Dir extends Object { contents: set Object }", "sig File extends Object { link: lone File }", "one sig Root extends Dir {}", "fact { all o: Object - Root | one contents.o", "  no Root.~contents", "  Object in Root.*contents }", "run { some d: Dir - Root | some d.contents & File and some Root.contents & File", "  some f: File | some f.link and contents.f != contents.(f.link) } for 6");

    /** The same tree, told from the children: each object names its parent. */
    static final String UP    = String.join("\n", "abstract sig Object { parent: lone Dir }", "sig Dir, File extends Object {}", "one sig Root extends Dir {}", "fact { no Root.parent and all o: Object - Root | Root in o.^parent }", "run { #File = 2 and #Dir = 2 } for 4");

    /** A tree told both ways: directories list their contents and every object names its parent. */
    static final String BOTH  = String.join("\n", "abstract sig Obj { parent: lone Dir }", "sig Dir extends Obj { contents: set Obj }", "sig File extends Obj {}", "one sig Root extends Dir {}", "fact { no Root.parent and contents = ~parent and Obj in Root.*contents }", "run { #Dir = 2 and #File = 2 } for 4");

    /** Files that are in one folder or in two, and three boxes that hold each other in a ring. */
    static final String MESSY = String.join("\n", "sig File {}", "sig Folder { holds: set File }", "sig Box { inside: one Box }", "fact { all b: Box | Box in b.^inside }", "run { #Box = 3 and #Folder = 2 and (some f: File | #holds.f = 2) and (some f: File | one holds.f) } for 3");

    /** Two folders and two files; one file moves to the other folder, then all stays as it is. */
    static final String MOVE  = String.join("\n", "sig File {}", "sig Folder { var holds: set File }", "fact { always all f: File | one holds.f }", "run { #Folder = 2 and #File = 2", "  some f: File, d: Folder | f not in d.holds and holds' = holds - (Folder -> f) + (d -> f)", "  after always holds' = holds } for 3");

    private static AlloyRelation rel(AlloyInstance inst, String name) {
        for (AlloyRelation r : inst.model.getRelations())
            if (r.getName().equals(name))
                return r;
        throw new IllegalArgumentException(name);
    }

    private static VizState theme(AlloyInstance inst, String... containers) {
        VizState theme = new VizState(inst);
        for (String c : containers)
            theme.container.put(rel(inst, c), true);
        return theme;
    }

    /** Every node of the graph and of the graphs nested in it, by path of layout keys. */
    private static Map<String,GraphNode> nodes(Graph g) {
        Map<String,GraphNode> ans = new LinkedHashMap<String,GraphNode>();
        collect(g, "", ans);
        return ans;
    }

    private static void collect(Graph g, String prefix, Map<String,GraphNode> out) {
        for (GraphNode n : g.nodes)
            if (n.layoutKey() != null) {
                out.put(prefix + n.layoutKey(), n);
                if (n.isContainer())
                    collect(n.inside(), prefix + n.layoutKey() + "/", out);
            }
    }

    private static List<GraphEdge> edges(Graph g, AlloyRelation rel) {
        List<GraphEdge> ans = new ArrayList<GraphEdge>();
        for (GraphEdge e : g.edges)
            if (rel.equals(e.group))
                ans.add(e);
        for (GraphNode n : g.nodes)
            if (n.isContainer())
                ans.addAll(edges(n.inside(), rel));
        return ans;
    }

    private static GraphNode node(Graph g, AlloyAtom atom) {
        GraphNode ans = null;
        for (GraphNode n : nodes(g).values())
            if (atom.equals(n.uuid)) {
                assertNull("two nodes for " + atom, ans);
                ans = n;
            }
        return ans;
    }

    @Test
    public void withoutTheSettingARelationIsDrawnAsArcs() throws Exception {
        AlloyInstance inst = TraceFixture.states(TREE).get(0);
        Graph g = StaticGraphMaker.buildGraph(inst, theme(inst), null);
        for (GraphNode n : g.nodes)
            assertFalse(n.isContainer());
        assertEquals(inst.relation2tuples(rel(inst, "contents")).size(), edges(g, rel(inst, "contents")).size());
    }

    @Test
    public void aContainerRelationPutsEachAtomInsideItsParent() throws Exception {
        AlloyInstance inst = TraceFixture.states(TREE).get(0);
        AlloyRelation contents = rel(inst, "contents");
        Graph g = StaticGraphMaker.buildGraph(inst, theme(inst, "contents"), null);
        assertEquals("only Root is outside", 1, g.nodes.size());
        assertEquals(inst.getAllAtoms().stream().filter(a -> !a.getType().isBuiltin).count(), nodes(g).size());
        assertTrue(inst.relation2tuples(contents).size() >= 4);
        for (AlloyTuple t : inst.relation2tuples(contents))
            assertSame(t.toString(), node(g, t.getStart()), node(g, t.getEnd()).container());
        assertEquals(0, edges(g, contents).size());
        assertTrue(g.crossEdges.isEmpty() || !contents.equals(g.crossEdges.get(0).group));
    }

    @Test
    public void otherRelationsBecomeEdgesInsideOrAcrossContainers() throws Exception {
        AlloyInstance inst = TraceFixture.states(TREE).get(0);
        AlloyRelation link = rel(inst, "link");
        VizState theme = theme(inst, "contents");
        theme.mergeArrows.put(null, false);
        Graph g = StaticGraphMaker.buildGraph(inst, theme, null);
        int across = 0;
        for (AlloyTuple t : inst.relation2tuples(link))
            if (node(g, t.getStart()).container() != node(g, t.getEnd()).container())
                across++;
        assertTrue(across > 0);
        int cross = 0;
        for (CrossEdge e : g.crossEdges) {
            assertEquals(link, e.group);
            assertTrue(inst.relation2tuples(link).contains(e.uuid));
            cross++;
        }
        assertEquals(across, cross);
        assertEquals(inst.relation2tuples(link).size() - across, edges(g, link).size());
        g.layout();
    }

    @Test
    public void layoutBackwardsMakesTheTargetTheContainer() throws Exception {
        AlloyInstance inst = TraceFixture.states(UP).get(0);
        AlloyRelation parent = rel(inst, "parent");
        VizState theme = theme(inst, "parent");
        theme.layoutBack.put(parent, true);
        Graph g = StaticGraphMaker.buildGraph(inst, theme, null);
        assertEquals(3, inst.relation2tuples(parent).size());
        for (AlloyTuple t : inst.relation2tuples(parent))
            assertSame(t.toString(), node(g, t.getEnd()), node(g, t.getStart()).container());
    }

    @Test
    public void relationsThatAgreeOnTheContainerNestTogether() throws Exception {
        AlloyInstance inst = TraceFixture.states(BOTH).get(0);
        VizState theme = new VizState(inst);
        MagicLayout.magic(theme);
        assertTrue(theme.container.resolve(rel(inst, "contents")));
        assertTrue(theme.container.resolve(rel(inst, "parent")));
        Graph g = StaticGraphMaker.buildGraph(inst, theme, null);
        assertEquals(1, g.nodes.size());
        assertEquals(4, nodes(g).size());
        assertEquals(0, edges(g, rel(inst, "contents")).size() + edges(g, rel(inst, "parent")).size() + g.crossEdges.size());
    }

    @Test
    public void anAtomWithTwoParentsOrInACycleStaysOutsideWithArcs() throws Exception {
        AlloyInstance inst = TraceFixture.states(MESSY).get(0);
        AlloyRelation holds = rel(inst, "holds"), inside = rel(inst, "inside");
        Graph g = StaticGraphMaker.buildGraph(inst, theme(inst, "holds", "inside"), null);
        int shared = 0, single = 0;
        for (AlloyAtom a : inst.getAllAtoms()) {
            int parents = 0;
            for (AlloyTuple t : inst.relation2tuples(holds))
                if (t.getEnd().equals(a))
                    parents++;
            if (parents == 1) {
                single++;
                assertNotNull(a.toString(), node(g, a).container());
            } else if (parents > 1) {
                shared += parents;
                assertNull(a.toString(), node(g, a).container());
            }
        }
        assertTrue(single > 0 && shared > 0);
        assertEquals(shared, edges(g, holds).size());
        assertEquals(3, edges(g, inside).size());
        for (AlloyAtom a : inst.getAllAtoms())
            if (a.getType().getName().equals("Box"))
                assertNull(node(g, a).container());
    }

    /** The file that changes folder, and the folders it leaves and enters. */
    private static String[] move(List<AlloyInstance> states) {
        AlloyRelation holds = rel(states.get(0), "holds");
        for (AlloyTuple before : states.get(0).relation2tuples(holds))
            for (AlloyTuple after : states.get(1).relation2tuples(rel(states.get(1), "holds")))
                if (before.getEnd().getOriginalName().equals(after.getEnd().getOriginalName()) && !before.getStart().getOriginalName().equals(after.getStart().getOriginalName()))
                    return new String[] {
                                         before.getEnd().getOriginalName(), before.getStart().getOriginalName(), after.getStart().getOriginalName()
                    };
        throw new IllegalStateException("no file moves");
    }

    @Test
    public void aMovedAtomLeavesAGhostBehindAndIsMarkedWhereItArrives() throws Exception {
        List<AlloyInstance> states = TraceFixture.states(MOVE);
        String[] m = move(states);
        Graph g = StaticGraphMaker.buildGraph(states.get(1), theme(states.get(1), "holds"), null, states.get(0));
        Map<String,GraphNode> nodes = nodes(g);
        assertEquals(DiffMark.REMOVED, nodes.get(m[1] + "/" + m[0]).diffMark());
        assertEquals(DiffMark.ADDED, nodes.get(m[2] + "/" + m[0]).diffMark());
        int marked = 0;
        for (GraphNode n : nodes.values())
            if (n.diffMark() != DiffMark.NONE)
                marked++;
        assertEquals(2, marked);
        g.layout();
    }

    @Test
    public void everyStateOfATraceKeepsContainersAndTheirContentInPlace() throws Exception {
        List<AlloyInstance> states = TraceFixture.states(MOVE);
        VizState theme = theme(states.get(0), "holds");
        LayoutTemplate template = TraceLayout.template(states, theme, null, null);
        String[] m = move(states);
        assertTrue(template.inner(m[1]).contains(m[0]));
        assertTrue(template.inner(m[2]).contains(m[0]));
        Map<String,java.awt.geom.Rectangle2D> seen = new LinkedHashMap<String,java.awt.geom.Rectangle2D>();
        for (int i = 0; i < 2; i++) {
            Graph g = StaticGraphMaker.buildGraph(states.get(i), theme, null, i == 0 ? null : states.get(i - 1));
            g.layout(template, true);
            for (Map.Entry<String,GraphNode> e : nodes(g).entrySet()) {
                java.awt.geom.Rectangle2D box = e.getValue().absoluteBox();
                if (seen.containsKey(e.getKey()))
                    assertEquals("state " + i + " " + e.getKey(), seen.get(e.getKey()), box);
                seen.put(e.getKey(), box);
            }
        }
        assertEquals(2 + 2 + 1, seen.size());
    }

    @Test
    public void theThemeFileRemembersTheSetting() throws Exception {
        AlloyInstance inst = TraceFixture.states(TREE).get(0);
        VizState theme = theme(inst, "contents");
        File file = File.createTempFile("theme", ".thm");
        file.deleteOnExit();
        StaticThemeReaderWriter.writeAlloy(file.getAbsolutePath(), theme);
        VizState loaded = new VizState(inst);
        assertFalse(loaded.container.resolve(rel(inst, "contents")));
        StaticThemeReaderWriter.readAlloy(file.getAbsolutePath(), loaded);
        assertTrue(loaded.container.resolve(rel(inst, "contents")));
        assertFalse(loaded.container.resolve(rel(inst, "link")));
    }

    @Test
    public void magicLayoutNestsRelationsThatAreNamedAndShapedLikeContainment() throws Exception {
        AlloyInstance tree = TraceFixture.states(TREE).get(0);
        VizState theme = new VizState(tree);
        MagicLayout.magic(theme);
        assertTrue(theme.container.resolve(rel(tree, "contents")));
        assertFalse(theme.container.resolve(rel(tree, "link")));

        AlloyInstance up = TraceFixture.states(UP).get(0);
        theme = new VizState(up);
        MagicLayout.magic(theme);
        assertTrue(theme.container.resolve(rel(up, "parent")));
        assertTrue(theme.layoutBack.resolve(rel(up, "parent")));

        AlloyInstance messy = TraceFixture.states(MESSY).get(0);
        theme = new VizState(messy);
        MagicLayout.magic(theme);
        assertFalse("shared files", theme.container.resolve(rel(messy, "holds")));
        assertFalse("a ring", theme.container.resolve(rel(messy, "inside")));
    }
}
