package edu.mit.csail.sdg.alloy4viz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.mit.csail.sdg.alloy4graph.Graph;
import edu.mit.csail.sdg.alloy4graph.GraphNode;
import edu.mit.csail.sdg.alloy4graph.LayoutTemplate;

public class TraceLayoutTest {

    private static File                xml;
    private static List<AlloyInstance> states;

    @BeforeClass
    public static void solve() throws Exception {
        xml = TraceFixture.solve(TraceFixture.TRASH);
        states = StaticInstanceReader.parseInstances(xml);
    }

    private static Set<String> names(AlloyInstance inst, String type) {
        Set<String> ans = new TreeSet<String>();
        for (AlloyAtom a : inst.getAllAtoms())
            if (a.getType().getName().equals(type))
                ans.add(a.getOriginalName());
        return ans;
    }

    @Test
    public void parseInstancesReturnsEveryStateOfTheTrace() throws Exception {
        assertEquals(states.get(0).originalA4.getTraceLength(), states.size());
        assertTrue(states.size() >= 4);
        for (int i = 0; i < states.size(); i++)
            assertEquals("state " + i, StaticInstanceReader.parseInstance(xml, i), states.get(i));
    }

    @Test
    public void parseInstancesReturnsOneStateForAStaticInstance() throws Exception {
        assertEquals(1, TraceFixture.states(TraceFixture.STATIC).size());
    }

    @Test
    public void theFixtureTraceLosesAFile() {
        assertEquals(3, names(states.get(0), "File").size());
        assertEquals(2, names(states.get(2), "File").size());
    }

    @Test
    public void unionHasEveryAtomAndTupleOfEveryState() {
        AlloyInstance union = TraceLayout.union(states);
        assertEquals(names(states.get(0), "File"), names(union, "File"));
        int most = 0, trashed = 0;
        for (AlloyInstance s : states) {
            for (AlloyRelation r : s.model.getRelations())
                if (r.getName().equals("keeps"))
                    most = Math.max(most, s.relation2tuples(r).size());
            for (AlloySet set : s.model.getSets())
                if (set.getName().equals("Trash"))
                    for (AlloyAtom a : s.set2atoms(set))
                        trashed++;
        }
        assertEquals(3, most);
        for (AlloyRelation r : union.model.getRelations())
            if (r.getName().equals("keeps"))
                assertEquals(3, union.relation2tuples(r).size());
        int unionTrashed = 0;
        for (AlloySet set : union.model.getSets())
            if (set.getName().equals("Trash"))
                unionTrashed = union.set2atoms(set).size();
        assertEquals("both trashed files are in the union's Trash", 2, unionTrashed);
        assertEquals(2, trashed);
    }

    @Test
    public void everyAtomStaysInOnePlaceThroughoutTheTrace() throws Exception {
        VizState view = new VizState(states.get(0));
        LayoutTemplate template = TraceLayout.template(states, view, null, null);
        Map<String,Point> seen = new LinkedHashMap<String,Point>();
        Graph all = StaticGraphMaker.buildGraph(TraceLayout.union(states), view, null);
        all.layout();
        for (GraphNode n : all.nodes)
            if (n.layoutKey() != null)
                seen.put(n.layoutKey(), new Point(n.x(), n.y()));
        Integer left = null, width = null;
        for (int i = 0; i < states.size(); i++) {
            VizState v = new VizState(view);
            v.loadInstance(states.get(i));
            Graph g = StaticGraphMaker.buildGraph(states.get(i), v, null);
            g.layout(template, true);
            for (GraphNode n : g.nodes) {
                if (n.layoutKey() == null)
                    continue; // a bend in a long edge
                Point here = new Point(n.x(), n.y()), before = seen.put(n.layoutKey(), here);
                if (before != null)
                    assertEquals(n.layoutKey() + " in state " + i, before, here);
            }
            if (left != null) {
                assertEquals("left edge in state " + i, left.intValue(), g.getLeft());
                assertEquals("width in state " + i, width.intValue(), g.getTotalWidth());
            }
            left = g.getLeft();
            width = g.getTotalWidth();
        }
        assertEquals("Root and three files", 4, seen.size());
    }
}
