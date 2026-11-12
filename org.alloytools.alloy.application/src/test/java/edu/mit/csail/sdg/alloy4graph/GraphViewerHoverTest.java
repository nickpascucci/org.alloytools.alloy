package edu.mit.csail.sdg.alloy4graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class GraphViewerHoverTest {

    private static GraphViewer viewer(String... names) {
        Graph g = new Graph(1.0);
        GraphNode first = null;
        for (String name : names) {
            GraphNode n = new GraphNode(g, name, name).setLayoutKey(name);
            if (first == null)
                first = n;
            else
                new GraphEdge(first, n, name, "", "rel");
        }
        return new GraphViewer(null, g);
    }

    private static GraphNode node(GraphViewer v, String key) {
        for (GraphNode n : v.getGraph().nodes)
            if (key.equals(n.layoutKey()))
                return n;
        throw new AssertionError(key);
    }

    private static void moveMouseOver(GraphViewer v, String key) {
        GraphNode n = node(v, key);
        int x = (int) ((n.x() - v.getGraph().getLeft()) * v.getScale()), y = (int) ((n.y() - v.getGraph().getTop()) * v.getScale());
        v.dispatchEvent(new MouseEvent(v, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }

    @Test
    public void hoveringTellsTheListenerWhichNodeItIs() {
        GraphViewer v = viewer("A", "B", "C");
        List<String> heard = new ArrayList<String>();
        v.setHoverListener(heard::add);
        moveMouseOver(v, "B");
        moveMouseOver(v, "B");
        moveMouseOver(v, "C");
        v.dispatchEvent(new MouseEvent(v, MouseEvent.MOUSE_EXITED, 0, 0, 0, 0, 0, false));
        List<String> expected = new ArrayList<String>();
        expected.add("B");
        expected.add("C");
        expected.add(null);
        assertEquals(expected, heard);
        assertNull(v.highlightedKey());
    }

    @Test
    public void highlightKeyHighlightsTheSameNodeInAnotherViewer() {
        GraphViewer one = viewer("A", "B", "C"), other = viewer("A", "C");
        one.setHoverListener(other::highlightKey);
        moveMouseOver(one, "C");
        assertEquals("C", other.highlightedKey());
        assertEquals("C", other.alloyGetHighlightedAnnotation());
        moveMouseOver(one, "B");
        assertNull("the other viewer has no B", other.highlightedKey());
    }

    @Test
    public void highlightKeyDoesNotEchoToTheListener() {
        GraphViewer v = viewer("A", "B");
        List<String> heard = new ArrayList<String>();
        v.setHoverListener(heard::add);
        v.highlightKey("B");
        assertEquals("B", v.highlightedKey());
        assertEquals(0, heard.size());
    }
}
