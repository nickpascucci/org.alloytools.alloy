package edu.mit.csail.sdg.alloy4graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class LayoutTemplateTest {

    /** A graph under construction, with nodes addressable by name. */
    private static final class G {

        final Graph                 graph = new Graph(1.0);
        final Map<String,GraphNode> nodes = new LinkedHashMap<String,GraphNode>();

        G nodes(String... names) {
            for (String n : names)
                nodes.put(n, new GraphNode(graph, n, n).setLayoutKey(n));
            return this;
        }

        G edge(String from, String to) {
            new GraphEdge(nodes.get(from), nodes.get(to), from + "->" + to, "", "rel");
            return this;
        }

        GraphNode n(String name) {
            return nodes.get(name);
        }

        /** Names of the real nodes of the same layer as the given node, left to right. */
        List<String> rowOf(String name) {
            List<GraphNode> row = new ArrayList<GraphNode>();
            for (GraphNode x : nodes.values())
                if (x.y() == n(name).y())
                    row.add(x);
            row.sort(Comparator.comparingInt(GraphNode::x));
            List<String> ans = new ArrayList<String>();
            for (GraphNode x : row)
                ans.add((String) x.uuid);
            return ans;
        }
    }

    private static G diamond() {
        return new G().nodes("A", "B", "C", "D").edge("A", "B").edge("A", "C").edge("B", "D").edge("C", "D");
    }

    private static void assertSamePlaces(G expected, G actual) {
        for (String name : actual.nodes.keySet()) {
            assertEquals("x of " + name, expected.n(name).x(), actual.n(name).x());
            assertEquals("y of " + name, expected.n(name).y(), actual.n(name).y());
        }
    }

    private static void assertNoOverlap(G g) {
        List<GraphNode> all = new ArrayList<GraphNode>(g.nodes.values());
        for (int i = 0; i < all.size(); i++)
            for (int j = i + 1; j < all.size(); j++)
                assertFalse(all.get(i).uuid + " overlaps " + all.get(j).uuid, all.get(i).getBoundingBox(0, 0).intersects(all.get(j).getBoundingBox(0, 0)));
    }

    @Test
    public void baselineLayoutPutsSinksAtTheBottom() {
        G g = diamond();
        g.graph.layout();
        assertTrue(g.n("A").y() < g.n("B").y());
        assertEquals(g.n("B").y(), g.n("C").y());
        assertTrue(g.n("B").y() < g.n("D").y());
        assertNoOverlap(g);
    }

    @Test
    public void captureRecordsEveryKeyedNode() {
        G g = diamond();
        g.graph.layout();
        LayoutTemplate t = g.graph.captureTemplate();
        assertEquals(4, t.size());
        assertTrue(t.contains("A"));
        assertFalse(t.contains("Z"));
    }

    @Test
    public void strictKeepsPlacesWhenNodesAndEdgesDisappear() {
        G all = diamond();
        all.graph.layout();
        LayoutTemplate t = all.graph.captureTemplate();

        // Laid out afresh, this graph is a single column; under the template C stays to one side.
        G part = new G().nodes("D", "C", "A").edge("A", "C").edge("C", "D");
        part.graph.layout(t, true);

        assertSamePlaces(all, part);
    }

    @Test
    public void strictKeepsPlacesWhenAnEdgeSpansLayers() {
        G all = diamond().edge("A", "D");
        all.graph.layout();
        LayoutTemplate t = all.graph.captureTemplate();

        G part = new G().nodes("A", "B", "C", "D").edge("A", "D");
        part.graph.layout(t, true);

        assertSamePlaces(all, part);
        assertNoOverlap(part);
    }

    @Test
    public void strictReversesEdgesThatPointUpTheTemplate() {
        G all = diamond();
        all.graph.layout();
        LayoutTemplate t = all.graph.captureTemplate();

        G part = new G().nodes("A", "B", "C", "D").edge("D", "B");
        part.graph.layout(t, true);

        assertSamePlaces(all, part);
        GraphEdge e = part.graph.edges.get(0);
        assertEquals("B", e.a().uuid);
        assertEquals("D", e.b().uuid);
        assertTrue("arrow head stays on B", e.ahead());
        assertFalse(e.bhead());
    }

    @Test
    public void strictKeepsTheDrawingAreaOfTheTemplate() {
        G all = diamond();
        all.graph.layout();
        LayoutTemplate t = all.graph.captureTemplate();

        G part = new G().nodes("A", "B").edge("A", "B");
        part.graph.layout(t, true);

        assertEquals(all.graph.getLeft(), part.graph.getLeft());
        assertEquals(all.graph.getTop(), part.graph.getTop());
        assertEquals(all.graph.getTotalWidth(), part.graph.getTotalWidth());
        assertEquals(all.graph.getTotalHeight(), part.graph.getTotalHeight());
    }

    @Test
    public void strictFallsBackWhenANodeHasNoSlot() {
        G all = diamond();
        all.graph.layout();
        LayoutTemplate t = all.graph.captureTemplate();

        G other = new G().nodes("A", "B", "NEW").edge("A", "B").edge("A", "NEW");
        other.graph.layout(t, true);

        assertTrue(other.n("A").y() < other.n("NEW").y());
        assertNoOverlap(other);
    }

    @Test
    public void softKeepsLeftToRightOrderOfKnownNodes() {
        G before = new G().nodes("R", "X", "Y", "Z").edge("R", "X").edge("R", "Y").edge("R", "Z");
        before.graph.layout();
        List<String> order = before.rowOf("X");
        assertEquals(3, order.size());
        LayoutTemplate t = before.graph.captureTemplate();

        // Created in the opposite order, so a fresh layout would mirror the row.
        G after = new G().nodes("R", order.get(2), "W", order.get(1), order.get(0));
        for (String child : new String[] {
                                          order.get(2), "W", order.get(1), order.get(0)
        })
            after.edge("R", child);
        G fresh = new G().nodes("R", order.get(2), "W", order.get(1), order.get(0));
        for (String child : new String[] {
                                          order.get(2), "W", order.get(1), order.get(0)
        })
            fresh.edge("R", child);
        fresh.graph.layout();
        List<String> freshOrder = fresh.rowOf("X");
        freshOrder.remove("W");
        assertFalse("the fresh layout must differ for this test to mean anything", order.equals(freshOrder));

        after.graph.layout(t, false);
        List<String> kept = after.rowOf("X");
        assertEquals(4, kept.size());
        kept.remove("W");
        assertEquals(order, kept);
        assertNoOverlap(after);
    }

    @Test
    public void softPullsALoneNodeBackToItsOldColumn() {
        G before = new G().nodes("R", "X", "Y", "Z").edge("R", "X").edge("R", "Y").edge("R", "Z");
        before.graph.layout();
        List<String> order = before.rowOf("X");
        String right = order.get(2);
        LayoutTemplate t = before.graph.captureTemplate();

        G after = new G().nodes("R", right).edge("R", right);
        G fresh = new G().nodes("R", right).edge("R", right);
        fresh.graph.layout();
        after.graph.layout(t, false);

        int old = before.n(right).x();
        assertTrue("closer to the old column than a fresh layout", Math.abs(after.n(right).x() - old) < Math.abs(fresh.n(right).x() - old));
    }
}
