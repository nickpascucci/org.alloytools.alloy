package edu.mit.csail.sdg.alloy4graph;

import java.awt.Color;

/**
 * How a node or an edge differs from the graph it is being compared with.
 * <p>
 * The colors are told apart under the common forms of color blindness (they are
 * from the palette of Okabe and Ito), and each mark also has its own badge and
 * line style, so that none of them depends on color alone.
 */
public enum DiffMark {

                      /** It is in both graphs, and the same in both. */
                      NONE(null),
                      /** It is new in this graph; drawn with a halo. */
                      ADDED(new Color(0, 158, 115)),
                      /**
                       * It was in the other graph and is gone from this one; drawn as a faded,
                       * dashed ghost.
                       */
                      REMOVED(new Color(213, 94, 0)),
                      /** It is in both graphs, with different properties; drawn with a halo. */
                      CHANGED(new Color(230, 159, 0));

    /** The color that stands for this mark; null for NONE. */
    public final Color color;

    private DiffMark(Color color) {
        this.color = color;
    }

    /** Returns the given color mixed with white, the way a ghost is drawn. */
    static Color fade(Color c) {
        return new Color(c.getRed() + (255 - c.getRed()) * 2 / 3, c.getGreen() + (255 - c.getGreen()) * 2 / 3, c.getBlue() + (255 - c.getBlue()) * 2 / 3);
    }

    /** Returns the given color mixed half and half with white, for a band under an edge. */
    static Color tint(Color c) {
        return new Color((c.getRed() + 255) / 2, (c.getGreen() + 255) / 2, (c.getBlue() + 255) / 2);
    }
}
