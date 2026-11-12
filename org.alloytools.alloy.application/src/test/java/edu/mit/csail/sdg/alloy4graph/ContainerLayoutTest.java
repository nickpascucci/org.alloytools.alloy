package edu.mit.csail.sdg.alloy4graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import edu.mit.csail.sdg.alloy4.OurPDFWriter;

public class ContainerLayoutTest {

    private static final Color BOX = new Color(255, 215, 0), CHILD = new Color(0, 200, 255), LINK = new Color(200, 0, 200);

    /**
     * A root directory that holds a file and a subdirectory with two files, next to
     * a user outside of them all.
     */
    private static final class Tree {

        final Graph                 graph = new Graph(1.0);
        final Map<String,GraphNode> nodes = new LinkedHashMap<String,GraphNode>();

        Tree(String... leftOut) {
            Set<String> skip = new HashSet<String>(java.util.Arrays.asList(leftOut));
            add("Root", graph, BOX);
            add("User", graph, CHILD);
            new GraphEdge(n("User"), n("Root"), "owns", "owns", "owns");
            add("Sub", n("Root").inside(), BOX);
            if (!skip.contains("F0"))
                add("F0", n("Root").inside(), CHILD);
            add("F1", n("Sub").inside(), CHILD);
            if (!skip.contains("F2")) {
                add("F2", n("Sub").inside(), CHILD);
                new GraphEdge(n("F1"), n("F2"), "link", "", "link");
            }
        }

        private void add(String name, Graph g, Color color) {
            nodes.put(name, new GraphNode(g, name, name).setLayoutKey(name).set(color));
        }

        GraphNode n(String name) {
            return nodes.get(name);
        }

        Rectangle2D box(String name) {
            return n(name).absoluteBox();
        }

        BufferedImage draw() {
            BufferedImage img = new BufferedImage(graph.getTotalWidth(), graph.getTotalHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            graph.draw(new Artist(g), 1.0, null, false);
            g.dispose();
            return img;
        }

        Color at(BufferedImage img, double x, double y) {
            return new Color(img.getRGB((int) x - graph.getLeft(), (int) y - graph.getTop()));
        }
    }

    @Test
    public void aContainerEnclosesWhatIsInsideIt() {
        Tree t = new Tree();
        t.graph.layout();
        assertTrue(t.box("Root").contains(t.box("Sub")));
        assertTrue(t.box("Root").contains(t.box("F0")));
        assertTrue(t.box("Sub").contains(t.box("F1")));
        assertTrue(t.box("Sub").contains(t.box("F2")));
        assertFalse(t.box("Sub").intersects(t.box("F0")));
        assertFalse(t.box("F1").intersects(t.box("F2")));
        assertFalse(t.box("Root").intersects(t.box("User")));
        assertTrue("F1 above F2", t.box("F1").getMaxY() < t.box("F2").getMinY());
        Rectangle2D all = new Rectangle2D.Double(t.graph.getLeft(), t.graph.getTop(), t.graph.getTotalWidth(), t.graph.getTotalHeight());
        assertTrue(all.contains(t.box("Root")));
    }

    @Test
    public void theLabelOfAContainerSitsAboveItsContent() {
        Tree t = new Tree();
        t.graph.layout();
        assertTrue(t.n("Root").labelBox().getMaxY() <= t.box("Sub").getMinY());
        assertTrue(t.n("Root").labelBox().getMaxY() <= t.box("F0").getMinY());
    }

    @Test
    public void findReturnsTheInnermostNode() {
        Tree t = new Tree();
        t.graph.layout();
        for (String name : new String[] {
                                         "F1", "F0", "User"
        }) {
            Rectangle2D box = t.box(name);
            assertSame(name, t.n(name), t.graph.find(1.0, (int) box.getCenterX() - t.graph.getLeft(), (int) box.getCenterY() - t.graph.getTop()));
        }
        Rectangle2D sub = t.box("Sub");
        assertSame(t.n("Sub"), t.graph.find(1.0, (int) sub.getMinX() + 2 - t.graph.getLeft(), (int) sub.getMinY() + 2 - t.graph.getTop()));
    }

    @Test
    public void containerAndContentAreBothDrawn() throws Exception {
        Tree t = new Tree();
        t.graph.layout();
        BufferedImage img = t.draw();
        Rectangle2D sub = t.box("Sub"), f1 = t.box("F1");
        assertEquals(DiffMark.tint(BOX), t.at(img, sub.getMinX() + 3, sub.getMinY() + 3));
        assertEquals(CHILD, t.at(img, f1.getMinX() + 3, f1.getMinY() + 3));
        File file = File.createTempFile("containers", ".pdf");
        file.deleteOnExit();
        OurPDFWriter pdf = new OurPDFWriter(file.getAbsolutePath(), 72, 1.0);
        t.graph.draw(new Artist(pdf), 1.0, null, false);
        pdf.close();
        assertTrue(file.length() > 500);
    }

    @Test
    public void anEdgeCanJoinNodesOfDifferentContainers() {
        Tree t = new Tree();
        CrossEdge e = t.graph.connect(t.n("F0"), t.n("F2"), "alias", "alias", "alias").set(LINK);
        t.graph.layout();
        Rectangle2D f0 = t.box("F0"), f2 = t.box("F2");
        assertTrue("starts at F0", grow(f0).contains(e.line().getP1()) && e.line().getP1().distance(f0.getCenterX(), f0.getCenterY()) > 5);
        assertTrue("ends at F2", grow(f2).contains(e.line().getP2()) && e.line().getP2().distance(f2.getCenterX(), f2.getCenterY()) > 5);
        assertTrue("the layout puts F0 above the directory that F2 is in", f0.getMaxY() < t.box("Sub").getMinY());
        BufferedImage img = t.draw();
        java.awt.geom.QuadCurve2D path = e.path();
        // The middle of a quadratic curve is halfway between the middle of its ends and its control point.
        double mx = (path.getX1() + path.getX2()) / 4 + path.getCtrlX() / 2, my = (path.getY1() + path.getY2()) / 4 + path.getCtrlY() / 2;
        boolean seen = false;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                seen |= LINK.equals(t.at(img, mx + dx, my + dy));
        assertTrue("the edge is drawn", seen);
        assertSame(e, t.graph.find(1.0, (int) mx - t.graph.getLeft(), (int) my - t.graph.getTop()));
    }

    @Test
    public void anEdgeAcrossContainersGoesAroundTheNodesInItsWay() {
        Tree t = new Tree();
        CrossEdge e = t.graph.connect(t.n("User"), t.n("F2"), "reads", "reads", "reads");
        t.graph.layout();
        java.awt.geom.QuadCurve2D path = e.path();
        for (String other : new String[] {
                                          "F0", "F1"
        })
            for (double f = 0.02; f < 1; f += 0.02) {
                double u = 1 - f;
                assertFalse(other + " at " + f, t.box(other).contains(u * u * path.getX1() + 2 * u * f * path.getCtrlX() + f * f * path.getX2(), u * u * path.getY1() + 2 * u * f * path.getCtrlY() + f * f * path.getY2()));
            }
    }

    private static Rectangle2D grow(Rectangle2D r) {
        return new Rectangle2D.Double(r.getX() - 3, r.getY() - 3, r.getWidth() + 6, r.getHeight() + 6);
    }

    @Test
    public void aTemplateKeepsContainersAndContentInPlace() {
        Tree all = new Tree();
        all.graph.layout();
        LayoutTemplate template = all.graph.captureTemplate();
        assertTrue(template.inner("Root").contains("Sub"));
        assertTrue(template.inner("Root").inner("Sub").contains("F2"));

        Tree part = new Tree("F0", "F2");
        part.graph.layout(template, true);
        for (String name : part.nodes.keySet())
            assertEquals(name, all.box(name), part.box(name));
        assertEquals(all.graph.getTotalWidth(), part.graph.getTotalWidth());
        assertEquals(all.graph.getTotalHeight(), part.graph.getTotalHeight());
    }

    @Test
    public void dotOutputNestsContainersAsClusters() {
        Tree t = new Tree();
        t.graph.connect(t.n("User"), t.n("F2"), "reads", "reads", "reads");
        t.graph.layout();
        String dot = t.graph.toString();
        assertEquals(2, dot.split("subgraph \"cluster", -1).length - 1);
        assertEquals(3, dot.split(" -> ", -1).length - 1);
        Set<String> names = new HashSet<String>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*(\"N[^\"]*\") \\[").matcher(dot);
        while (m.find())
            assertTrue("node declared twice: " + m.group(1), names.add(m.group(1)));
        assertEquals(6, names.size());
    }
}
