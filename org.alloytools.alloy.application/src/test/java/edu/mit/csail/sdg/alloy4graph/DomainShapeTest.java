package edu.mit.csail.sdg.alloy4graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

import org.junit.Test;

import edu.mit.csail.sdg.alloy4.OurPDFWriter;

public class DomainShapeTest {

    private static final DotShape[] DOMAIN = {
                                              DotShape.FOLDER, DotShape.DOCUMENT, DotShape.ENVELOPE, DotShape.MAILBOX, DotShape.LOCK, DotShape.KEY, DotShape.PERSON, DotShape.DATABASE
    };

    private final Graph             graph  = new Graph(1.0);

    private GraphNode node(DotShape shape) {
        GraphNode n = new GraphNode(graph, shape, "A label", "second line").set(shape).set(Color.YELLOW);
        graph.layout();
        return n;
    }

    /** Whether the node covers the point at the given fractions of its bounding box. */
    private static boolean covers(GraphNode n, double fx, double fy) {
        Rectangle2D box = n.getBoundingBox(0, 0);
        return n.contains(box.getMinX() + fx * box.getWidth(), box.getMinY() + fy * box.getHeight());
    }

    @Test
    public void everyDomainShapeHasAnIconAndSurvivesTheThemeFile() throws Exception {
        for (DotShape s : DOMAIN) {
            assertEquals(s, DotShape.parse(s.toString()));
            File icon = new File("src/main/resources/icons/ShapeIcons/" + s.toString().toLowerCase() + ".gif");
            assertEquals(s + " icon", 15, javax.imageio.ImageIO.read(icon).getWidth());
        }
    }

    @Test
    public void everyDomainShapeIsLargerThanABoxAndCoversItsLabel() {
        GraphNode box = node(DotShape.BOX);
        int boxArea = box.getWidth() * box.getHeight();
        for (DotShape s : DOMAIN) {
            GraphNode n = node(s);
            assertTrue(s + " area", n.getWidth() * n.getHeight() > boxArea);
            assertTrue(s + " top", n.getBoundingBox(0, 0).getMinY() < n.labelBox().getMinY() + 0.5);
            Rectangle2D label = n.labelBox();
            for (double fx = 0.1; fx < 1; fx += 0.2)
                for (double fy = 0.1; fy < 1; fy += 0.2)
                    assertTrue(s + " label " + fx + "," + fy, n.contains(label.getMinX() + fx * label.getWidth(), label.getMinY() + fy * label.getHeight()));
        }
    }

    @Test
    public void folderHasATabOnTheLeft() {
        GraphNode n = node(DotShape.FOLDER);
        assertTrue(covers(n, 0.05, 0.02));
        assertFalse(covers(n, 0.95, 0.02));
        assertTrue(covers(n, 0.95, 0.5));
    }

    @Test
    public void documentHasAFoldedCorner() {
        GraphNode n = node(DotShape.DOCUMENT);
        assertTrue(covers(n, 0.02, 0.02));
        assertFalse(covers(n, 0.99, 0.01));
        assertTrue(covers(n, 0.98, 0.98));
    }

    @Test
    public void envelopeIsARectangleWithAFlap() {
        GraphNode n = node(DotShape.ENVELOPE);
        assertTrue(covers(n, 0.02, 0.02) && covers(n, 0.98, 0.02) && covers(n, 0.02, 0.98));
        assertNotNull(n.decoration());
    }

    @Test
    public void mailboxIsAnArchOnAPost() {
        GraphNode n = node(DotShape.MAILBOX);
        assertTrue(covers(n, 0.5, 0.02));
        assertFalse(covers(n, 0.01, 0.01));
        assertTrue(covers(n, 0.5, 0.98));
        assertFalse(covers(n, 0.05, 0.98));
    }

    @Test
    public void lockHasAnOpenShackle() {
        GraphNode n = node(DotShape.LOCK);
        Rectangle2D box = n.getBoundingBox(0, 0), label = n.labelBox();
        assertFalse("corner", covers(n, 0.02, 0.02));
        assertTrue("shackle top", n.contains(box.getCenterX(), box.getMinY() + 2));
        assertFalse("shackle hole", n.contains(box.getCenterX(), label.getMinY() - 3));
    }

    @Test
    public void keyHasABowAboveABlade() {
        GraphNode n = node(DotShape.KEY);
        assertTrue(covers(n, 0.5, 0.97));
        assertFalse(covers(n, 0.05, 0.97));
        assertFalse(covers(n, 0.02, 0.02));
    }

    @Test
    public void personHasAHeadAboveTheShoulders() {
        GraphNode n = node(DotShape.PERSON);
        assertTrue(covers(n, 0.5, 0.03));
        assertFalse(covers(n, 0.1, 0.03));
        assertTrue(covers(n, 0.05, 0.9));
    }

    @Test
    public void databaseIsACylinder() {
        GraphNode n = node(DotShape.DATABASE);
        assertFalse(covers(n, 0.01, 0.01));
        assertFalse(covers(n, 0.99, 0.99));
        assertTrue(covers(n, 0.5, 0.01) && covers(n, 0.5, 0.99) && covers(n, 0.01, 0.5));
        assertNotNull(n.decoration());
    }

    @Test
    public void domainShapesDrawOnScreenAndInPdf() throws Exception {
        for (DotShape s : DOMAIN)
            new GraphNode(graph, s, s.toString()).set(s).set(Color.YELLOW).set(DiffMark.ADDED);
        graph.layout();
        BufferedImage img = new BufferedImage(graph.getTotalWidth(), graph.getTotalHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        graph.draw(new Artist(g), 1.0, null, false);
        g.dispose();
        for (GraphNode n : graph.nodes) {
            Rectangle2D label = n.labelBox();
            assertEquals(n.uuid.toString(), Color.YELLOW, new Color(img.getRGB((int) label.getMinX() + 2 - graph.getLeft(), (int) label.getMaxY() - 2 - graph.getTop())));
        }
        File pdf = File.createTempFile("shapes", ".pdf");
        pdf.deleteOnExit();
        OurPDFWriter out = new OurPDFWriter(pdf.getAbsolutePath(), 72, 1.0);
        graph.draw(new Artist(out), 1.0, null, false);
        out.close();
        assertTrue(pdf.length() > 500);
    }
}
