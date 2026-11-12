package edu.mit.csail.sdg.alloy4graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import edu.mit.csail.sdg.alloy4.OurPDFWriter;

public class DiffRenderTest {

    private static final Color NODE = new Color(255, 215, 0), EDGE = new Color(0, 0, 255);

    private final Graph        graph = new Graph(1.0);
    private final GraphNode    top   = new GraphNode(graph, "top", "top").set(NODE);
    private final GraphNode    left  = new GraphNode(graph, "left", "left").set(NODE);
    private final GraphNode    right = new GraphNode(graph, "right", "right").set(NODE);
    private final GraphEdge    toLeft, toRight;

    public DiffRenderTest() {
        toLeft = new GraphEdge(top, left, "l", "", "rel").set(EDGE);
        toRight = new GraphEdge(top, right, "r", "", "rel").set(EDGE);
    }

    /** Lays out and draws the graph without smoothing, so that every pixel has an exact color. */
    private BufferedImage draw() {
        graph.layout();
        BufferedImage img = new BufferedImage(graph.getTotalWidth(), graph.getTotalHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        graph.draw(new Artist(g), 1.0, null, false);
        g.dispose();
        return img;
    }

    private Color at(BufferedImage img, double x, double y) {
        return new Color(img.getRGB((int) x - graph.getLeft(), (int) y - graph.getTop()));
    }

    private static Set<Color> colors(BufferedImage img) {
        Set<Color> ans = new HashSet<Color>();
        for (int x = 0; x < img.getWidth(); x++)
            for (int y = 0; y < img.getHeight(); y++)
                ans.add(new Color(img.getRGB(x, y)));
        return ans;
    }

    /** The color just outside the middle of the left side of the node. */
    private Color outside(BufferedImage img, GraphNode n) {
        Rectangle2D box = n.getBoundingBox(0, 0);
        return at(img, box.getMinX() - 3, box.getCenterY());
    }

    /** The color just inside the middle of the left side of the node. */
    private Color inside(BufferedImage img, GraphNode n) {
        Rectangle2D box = n.getBoundingBox(0, 0);
        return at(img, box.getMinX() + 3, box.getCenterY());
    }

    @Test
    public void unmarkedGraphHasNoMarkColors() {
        Set<Color> seen = colors(draw());
        assertTrue(seen.contains(NODE));
        assertTrue(seen.contains(EDGE));
        for (DiffMark m : DiffMark.values())
            if (m != DiffMark.NONE)
                assertFalse(m.toString(), seen.contains(m.color));
    }

    @Test
    public void addedNodeHasAHaloAroundIt() {
        left.set(DiffMark.ADDED);
        BufferedImage img = draw();
        assertEquals(DiffMark.ADDED.color, outside(img, left));
        assertEquals(NODE, inside(img, left));
        assertEquals(Color.WHITE, outside(img, right));
    }

    @Test
    public void changedNodeHasAHaloOfItsOwnColor() {
        left.set(DiffMark.CHANGED);
        assertEquals(DiffMark.CHANGED.color, outside(draw(), left));
    }

    @Test
    public void removedNodeIsFadedAndHasNoHalo() {
        left.set(DiffMark.REMOVED);
        BufferedImage img = draw();
        assertEquals(DiffMark.fade(NODE), inside(img, left));
        assertEquals(Color.WHITE, outside(img, left));
        assertEquals(NODE, inside(img, right));
        assertTrue("the badge", colors(img).contains(DiffMark.REMOVED.color));
    }

    @Test
    public void removedEdgeIsFaded() {
        toLeft.set(DiffMark.REMOVED);
        toRight.set(DiffMark.REMOVED);
        Set<Color> seen = colors(draw());
        assertFalse(seen.contains(EDGE));
        assertTrue(seen.contains(DiffMark.fade(EDGE)));
    }

    @Test
    public void addedEdgeIsUnderlined() {
        toLeft.set(DiffMark.ADDED);
        Set<Color> seen = colors(draw());
        assertTrue(seen.contains(EDGE));
        assertTrue(seen.contains(DiffMark.tint(DiffMark.ADDED.color)));
    }

    @Test
    public void marksSurviveExportToPdf() throws Exception {
        left.set(DiffMark.ADDED);
        right.set(DiffMark.REMOVED);
        toLeft.set(DiffMark.ADDED);
        toRight.set(DiffMark.REMOVED);
        graph.layout();
        File file = File.createTempFile("marks", ".pdf");
        file.deleteOnExit();
        OurPDFWriter pdf = new OurPDFWriter(file.getAbsolutePath(), 72, 1.0);
        graph.draw(new Artist(pdf), 1.0, null, false);
        pdf.close();
        assertTrue(file.length() > 500);
    }
}
