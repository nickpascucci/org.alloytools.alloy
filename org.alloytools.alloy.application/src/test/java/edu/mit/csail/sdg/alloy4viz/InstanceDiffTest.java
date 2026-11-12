package edu.mit.csail.sdg.alloy4viz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.mit.csail.sdg.alloy4graph.DiffMark;
import edu.mit.csail.sdg.alloy4graph.Graph;
import edu.mit.csail.sdg.alloy4graph.GraphEdge;
import edu.mit.csail.sdg.alloy4graph.GraphNode;
import edu.mit.csail.sdg.alloy4graph.LayoutTemplate;

/**
 * The fixture trace: s0 has three files; in s1 one of them is in the trash; in
 * s2 that file is gone; in s3 another file is in the trash.
 */
public class InstanceDiffTest {

    private static List<AlloyInstance> s;
    /** The file that is trashed in s1 and gone in s2. */
    private static String              doomed;

    @BeforeClass
    public static void solve() throws Exception {
        s = TraceFixture.states(TraceFixture.TRASH);
        Set<String> gone = files(s.get(0));
        gone.removeAll(files(s.get(2)));
        assertEquals(1, gone.size());
        doomed = gone.iterator().next();
    }

    private static Set<String> files(AlloyInstance inst) {
        Set<String> ans = new TreeSet<String>();
        for (AlloyAtom a : inst.getAllAtoms())
            if (a.getType().getName().equals("File"))
                ans.add(a.getOriginalName());
        return ans;
    }

    private static AlloyRelation keeps(AlloyInstance inst) {
        for (AlloyRelation r : inst.model.getRelations())
            if (r.getName().equals("keeps"))
                return r;
        throw new AssertionError("no relation named keeps");
    }

    private static AlloyTuple keepsOf(AlloyInstance inst, String file) {
        for (AlloyTuple t : inst.relation2tuples(keeps(inst)))
            if (t.getEnd().getOriginalName().equals(file))
                return t;
        throw new AssertionError("nothing keeps " + file);
    }

    private static GraphNode node(Graph g, String key) {
        for (GraphNode n : g.nodes)
            if (key.equals(n.layoutKey()))
                return n;
        return null;
    }

    private static int count(Graph g, DiffMark mark) {
        int ans = 0;
        for (GraphNode n : g.nodes)
            if (n.layoutKey() != null && n.diffMark() == mark)
                ans++;
        return ans;
    }

    @Test
    public void identicalInstancesHaveNoDifference() {
        assertTrue(InstanceDiff.between(s.get(0), s.get(0)).isEmpty());
    }

    @Test
    public void trashingAFileMovesItAndDropsItsTuple() {
        InstanceDiff d = InstanceDiff.between(s.get(0), s.get(1));
        assertFalse(d.isEmpty());
        assertEquals(Collections.singleton(doomed), d.movedAtoms());
        assertTrue(d.addedAtoms().isEmpty());
        assertTrue(d.removedAtoms().isEmpty());
        AlloyTuple lost = keepsOf(s.get(0), doomed);
        assertTrue(d.isRemoved(keeps(s.get(0)), lost));
        assertFalse(d.isAdded(keeps(s.get(0)), lost));
        assertTrue(d.differsFrom(keeps(s.get(0)), "Root$0"));
        assertFalse(d.differsFrom(keeps(s.get(0)), doomed));
        for (AlloyTuple kept : s.get(1).relation2tuples(keeps(s.get(1))))
            assertFalse(d.isRemoved(keeps(s.get(1)), kept));
    }

    @Test
    public void emptyingTheTrashRemovesTheFile() {
        InstanceDiff d = InstanceDiff.between(s.get(1), s.get(2));
        assertEquals(Collections.singleton(doomed), d.removedAtoms());
        assertTrue(d.addedAtoms().isEmpty());
        assertTrue(d.movedAtoms().isEmpty());
    }

    @Test
    public void goingBackwardsAddsWhatWasRemoved() {
        InstanceDiff d = InstanceDiff.between(s.get(2), s.get(1));
        assertEquals(Collections.singleton(doomed), d.addedAtoms());
        d = InstanceDiff.between(s.get(1), s.get(0));
        assertTrue(d.isAdded(keeps(s.get(0)), keepsOf(s.get(0), doomed)));
    }

    @Test
    public void graphWithoutBaselineHasNoMarks() throws Exception {
        Graph g = StaticGraphMaker.buildGraph(s.get(1), new VizState(s.get(1)), null);
        assertEquals(4, count(g, DiffMark.NONE));
        for (GraphEdge e : g.edges)
            assertEquals(DiffMark.NONE, e.diffMark());
    }

    @Test
    public void graphMarksAMovedAtomAndKeepsItsLostEdgeAsAGhost() throws Exception {
        Graph g = StaticGraphMaker.buildGraph(s.get(1), new VizState(s.get(1)), null, s.get(0));
        assertEquals(DiffMark.CHANGED, node(g, doomed).diffMark());
        assertEquals(DiffMark.NONE, node(g, "Root$0").diffMark());
        assertEquals(1, count(g, DiffMark.CHANGED));
        assertEquals(3, count(g, DiffMark.NONE));
        int ghosts = 0, plain = 0;
        for (GraphEdge e : g.edges)
            if (e.diffMark() == DiffMark.REMOVED) {
                ghosts++;
                assertEquals(doomed, e.b().layoutKey());
            } else if (e.diffMark() == DiffMark.NONE)
                plain++;
        assertEquals(1, ghosts);
        assertEquals(2, plain);
    }

    @Test
    public void graphKeepsARemovedAtomAsAGhost() throws Exception {
        Graph g = StaticGraphMaker.buildGraph(s.get(2), new VizState(s.get(2)), null, s.get(1));
        assertEquals(DiffMark.REMOVED, node(g, doomed).diffMark());
        assertEquals(1, count(g, DiffMark.REMOVED));
        assertEquals(3, count(g, DiffMark.NONE));
        for (GraphEdge e : g.edges)
            assertEquals(DiffMark.NONE, e.diffMark());
    }

    @Test
    public void graphMarksANewAtomAndItsEdge() throws Exception {
        Graph g = StaticGraphMaker.buildGraph(s.get(0), new VizState(s.get(0)), null, s.get(2));
        assertEquals(DiffMark.ADDED, node(g, doomed).diffMark());
        int added = 0;
        for (GraphEdge e : g.edges)
            if (e.diffMark() == DiffMark.ADDED)
                added++;
        assertEquals(1, added);
    }

    @Test
    public void ghostsTakeTheirPlaceFromTheTraceTemplate() throws Exception {
        VizState view = new VizState(s.get(0));
        LayoutTemplate t = TraceLayout.template(s, view, null, null);
        Graph before = StaticGraphMaker.buildGraph(s.get(1), view, null);
        before.layout(t, true);
        Graph after = StaticGraphMaker.buildGraph(s.get(2), view, null, s.get(1));
        after.layout(t, true);
        assertEquals(node(before, doomed).x(), node(after, doomed).x());
        assertEquals(node(before, doomed).y(), node(after, doomed).y());
    }
}
