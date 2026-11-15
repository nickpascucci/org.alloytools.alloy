package edu.mit.csail.sdg.alloy4graph;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.Test;

import edu.mit.csail.sdg.alloy4.OurTheme;

public class ArtistThemeTest {

    private static final Color GOLD = new Color(255, 215, 0);

    /** Fills a dot with the given color through the artist and returns the color that landed. */
    private static Color paint(boolean darkCanvas, Color color) {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Artist artist = new Artist(g, darkCanvas);
        artist.translate(10, 10);
        artist.setColor(color);
        artist.fillCircle(8);
        g.dispose();
        return new Color(img.getRGB(10, 10));
    }

    @Test
    public void lightCanvasKeepsColors() {
        assertEquals(GOLD, paint(false, GOLD));
        assertEquals(Color.BLACK, paint(false, Color.BLACK));
    }

    @Test
    public void darkCanvasCarriesColorsOver() {
        assertEquals(OurTheme.onDark(GOLD), paint(true, GOLD));
        assertEquals(OurTheme.onDark(Color.BLACK), paint(true, Color.BLACK));
        assertEquals(OurTheme.onDark(Color.WHITE), paint(true, Color.WHITE));
    }

    /** A whole graph drawn on the dark canvas uses only carried-over colors. */
    @Test
    public void graphOnDarkCanvasHasLightInkAndNoWhite() {
        Graph graph = new Graph(1.0);
        GraphNode a = new GraphNode(graph, "a", "a").set(Color.WHITE);
        GraphNode b = new GraphNode(graph, "b", "b").set(GOLD);
        new GraphEdge(a, b, "e", "", "rel").set(Color.BLACK);
        graph.layout();
        BufferedImage img = new BufferedImage(graph.getTotalWidth(), graph.getTotalHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Color canvas = OurTheme.onDark(Color.WHITE);
        g.setColor(canvas);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        graph.draw(new Artist(g, true), 1.0, null, false);
        g.dispose();
        boolean ink = false;
        for (int x = 0; x < img.getWidth(); x++)
            for (int y = 0; y < img.getHeight(); y++) {
                Color c = new Color(img.getRGB(x, y));
                if (c.equals(Color.WHITE) || c.equals(Color.BLACK))
                    throw new AssertionError("uncarried color " + c + " at " + x + "," + y);
                ink |= c.equals(OurTheme.onDark(Color.BLACK));
            }
        assertEquals("outlines and labels are drawn in light ink", true, ink);
    }
}
