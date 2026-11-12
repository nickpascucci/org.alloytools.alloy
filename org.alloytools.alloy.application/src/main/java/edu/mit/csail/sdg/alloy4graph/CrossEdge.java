package edu.mit.csail.sdg.alloy4graph;

import static edu.mit.csail.sdg.alloy4graph.Graph.esc;

import java.awt.Color;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable; an edge between two nodes that are not in the same graph: at least
 * one of them is inside a container. It is drawn on top of the containers, as
 * a straight line or, where that would run across another node, as a curve
 * that goes around it if there is one.
 * <p>
 * <b>Thread Safety:</b> Can be called only by the AWT event thread.
 */
public final strictfp class CrossEdge {

    /** A user-provided annotation that will be associated with this edge (can be null). */
    public final Object     uuid;

    /** The group that this edge belongs to; edges of one group are highlighted together. */
    public final Object     group;

    /** The two ends. */
    private final GraphNode a, b;

    /** The label (can be empty). */
    private final String    label;

    /**
     * The hidden edge through which the layout takes this edge into account; null
     * if one end is inside the other.
     */
    private final GraphEdge proxy;

    private boolean         ahead    = false, bhead = true;

    private Color           color    = Color.BLACK;

    private DotStyle        style    = DotStyle.SOLID;

    private DiffMark        diffMark = DiffMark.NONE;

    private final int       ad       = Artist.getMaxAscentAndDescent();

    CrossEdge(GraphNode from, GraphNode to, Object uuid, String label, Object group, GraphEdge proxy) {
        this.a = from;
        this.b = to;
        this.uuid = uuid;
        this.label = label == null ? "" : label;
        this.group = group;
        this.proxy = proxy;
    }

    /** Returns the "from" node. */
    public GraphNode a() {
        return a;
    }

    /** Returns the "to" node. */
    public GraphNode b() {
        return b;
    }

    /** Sets the color of the edge. */
    public CrossEdge set(Color color) {
        if (color != null)
            this.color = color;
        return this;
    }

    /** Sets the line style of the edge. */
    public CrossEdge set(DotStyle style) {
        if (style != null)
            this.style = style;
        return this;
    }

    /** Sets whether there is an arrow head on the "from" node and on the "to" node. */
    public CrossEdge set(boolean from, boolean to) {
        ahead = from;
        bhead = to;
        return this;
    }

    /** Sets how much the layout tries to keep this edge short, as GraphEdge does. */
    public CrossEdge set(int weightBetween1And10000) {
        if (proxy != null)
            proxy.set(weightBetween1And10000);
        return this;
    }

    /** Sets how this edge differs from the graph it is compared with. */
    public CrossEdge set(DiffMark mark) {
        diffMark = mark == null ? DiffMark.NONE : mark;
        return this;
    }

    /** Returns how this edge differs from the graph it is compared with. */
    public DiffMark diffMark() {
        return diffMark;
    }

    /**
     * Returns where this edge touches the given end. That is the middle of the
     * node; for a container that the other end is inside of, the middle of its
     * labels.
     */
    private static Point2D anchor(GraphNode n, GraphNode other) {
        if (!other.isInside(n))
            return new Point2D.Double(n.absX(), n.absY());
        Rectangle2D head = n.labelBox();
        return new Point2D.Double(n.absX() - n.x() + head.getCenterX(), n.absY() - n.y() + head.getCenterY());
    }

    /** Returns the point where the line from a point inside the box to another point leaves the box. */
    private static Point2D leave(Rectangle2D box, Point2D inside, Point2D to) {
        final double dx = to.getX() - inside.getX(), dy = to.getY() - inside.getY();
        double t = 1;
        if (dx > 0)
            t = Math.min(t, (box.getMaxX() - inside.getX()) / dx);
        if (dx < 0)
            t = Math.min(t, (box.getMinX() - inside.getX()) / dx);
        if (dy > 0)
            t = Math.min(t, (box.getMaxY() - inside.getY()) / dy);
        if (dy < 0)
            t = Math.min(t, (box.getMinY() - inside.getY()) / dy);
        return new Point2D.Double(inside.getX() + t * dx, inside.getY() + t * dy);
    }

    /**
     * Assuming the graph is laid out, returns the line from the first node to the
     * second, in the coordinates of the outermost graph. It stops at the outline
     * of each node, except that of a container that the other end is inside of.
     */
    Line2D line() {
        final Point2D p = anchor(a, b), q = anchor(b, a);
        final Point2D from = b.isInside(a) ? p : leave(a.absoluteBox(), p, q);
        final Point2D to = a.isInside(b) ? q : leave(b.absoluteBox(), q, p);
        return new Line2D.Double(from, to);
    }

    /** How far the middle of the curve may be from the straight line, in the order tried. */
    private static final int[] bows  = {
                                        0, 24, -24, 48, -48, 80, -80, 120, -120
    };

    /** The number of points at which a curve is checked and measured. */
    private static final int   steps = 24;

    /**
     * Assuming the graph is laid out, returns the curve this edge is drawn as, in
     * the coordinates of the outermost graph: from line(), the first of the curves
     * tried that touches no node other than the two ends and the containers, or the
     * straight line if all of them touch one.
     */
    QuadCurve2D path() {
        final Line2D at = line();
        final List<Rectangle2D> obstacles = new ArrayList<Rectangle2D>();
        collectObstacles(a.graph.outermost(), obstacles);
        final double dx = at.getX2() - at.getX1(), dy = at.getY2() - at.getY1(), len = Math.max(1, Math.sqrt(dx * dx + dy * dy));
        QuadCurve2D straight = null;
        for (int bow : bows) {
            // The control point of a quadratic curve is twice as far out as its middle.
            QuadCurve2D c = new QuadCurve2D.Double(at.getX1(), at.getY1(), (at.getX1() + at.getX2()) / 2 - dy / len * 2 * bow, (at.getY1() + at.getY2()) / 2 + dx / len * 2 * bow, at.getX2(), at.getY2());
            if (straight == null)
                straight = c;
            boolean clear = true;
            for (int i = 1; i < steps && clear; i++) {
                Point2D p = pointAt(c, i / (double) steps);
                for (Rectangle2D box : obstacles)
                    if (box.contains(p)) {
                        clear = false;
                        break;
                    }
            }
            if (clear)
                return c;
        }
        return straight;
    }

    /** Adds the bounds of every node that this edge should not run across. */
    private void collectObstacles(Graph g, List<Rectangle2D> out) {
        for (GraphNode n : g.nodes) {
            if (n.shape() == null || n == a || n == b)
                continue;
            if (n.isContainer())
                collectObstacles(n.inside(), out);
            else {
                Rectangle2D box = n.absoluteBox();
                out.add(new Rectangle2D.Double(box.getX() - 4, box.getY() - 4, box.getWidth() + 8, box.getHeight() + 8));
            }
        }
    }

    /** Returns the point of the curve at the given fraction (0..1) of the way. */
    private static Point2D pointAt(QuadCurve2D c, double t) {
        final double u = 1 - t;
        return new Point2D.Double(u * u * c.getX1() + 2 * u * t * c.getCtrlX() + t * t * c.getX2(), u * u * c.getY1() + 2 * u * t * c.getCtrlY() + t * t * c.getY2());
    }

    /** Returns true if the given point of the outermost graph is within the given distance of this edge. */
    boolean isNear(double x, double y, double distance) {
        final QuadCurve2D c = path();
        Point2D from = c.getP1();
        for (int i = 1; i <= steps; i++) {
            Point2D to = pointAt(c, i / (double) steps);
            if (Line2D.ptSegDist(from.getX(), from.getY(), to.getX(), to.getY(), x, y) <= distance)
                return true;
            from = to;
        }
        return false;
    }

    /** The places along the curve where the label may go, in the order tried. */
    private static final double[] labelAt = {
                                             0.5, 0.36, 0.64, 0.43, 0.57, 0.28, 0.72, 0.2, 0.8
    };

    /**
     * Assuming the graph is laid out, draws this edge, its label and its arrow
     * heads; the origin of the artist is the top left corner of the outermost
     * graph.
     *
     * @param taken where the labels of other edges are; the label goes to the first
     *            free place along the curve, and that place is added to the list
     */
    void draw(Artist gr, double scale, boolean highlighted, boolean dimmed, List<Rectangle2D> taken) {
        final Graph root = a.graph.outermost();
        final QuadCurve2D at = path();
        final double ox = root.getLeft(), oy = root.getTop();
        final QuadCurve2D curve = new QuadCurve2D.Double(at.getX1() - ox, at.getY1() - oy, at.getCtrlX() - ox, at.getCtrlY() - oy, at.getX2() - ox, at.getY2() - oy);
        final GeneralPath line = new GeneralPath(curve);
        final Color shade = dimmed ? Color.LIGHT_GRAY : (diffMark == DiffMark.REMOVED ? DiffMark.fade(color) : color);
        if (diffMark == DiffMark.ADDED && !dimmed) {
            gr.setColor(DiffMark.tint(diffMark.color));
            gr.setStroke(7f, null, scale);
            gr.draw(line, false);
        }
        gr.setColor(shade);
        if (highlighted)
            gr.set(DotStyle.BOLD, scale);
        else if (diffMark == DiffMark.REMOVED)
            gr.setStroke(1.3f, GraphNode.ghostDash, scale);
        else
            gr.set(style, scale);
        gr.draw(line, false);
        gr.set(DotStyle.SOLID, scale);
        if (bhead)
            arrowHead(gr, curve.getCtrlX(), curve.getCtrlY(), curve.getX2(), curve.getY2());
        if (ahead)
            arrowHead(gr, curve.getCtrlX(), curve.getCtrlY(), curve.getX1(), curve.getY1());
        if (label.length() > 0 && !dimmed) {
            final Rectangle2D size = Artist.getBounds(false, label);
            Rectangle2D place = null;
            for (double f : labelAt) {
                Point2D p = pointAt(curve, f);
                Rectangle2D r = new Rectangle2D.Double(p.getX() + 4, p.getY() - 3 - Artist.getMaxAscent(), size.getWidth(), ad);
                boolean free = true;
                for (Rectangle2D other : taken)
                    free &= !other.intersects(r);
                if (place == null || free)
                    place = r;
                if (free)
                    break;
            }
            taken.add(place);
            gr.setFont(false);
            gr.drawString(label, (int) place.getX(), (int) place.getY() + Artist.getMaxAscent());
        }
    }

    /** Draws an arrow head at the second point, as if on a line from the first point. */
    private void arrowHead(Artist gr, double fromX, double fromY, double tipX, double tipY) {
        final double length = ad * 0.6, fan = Math.toRadians(highFan), t = Math.PI + Math.atan2(tipY - fromY, tipX - fromX);
        GeneralPath gp = new GeneralPath();
        gp.moveTo((float) (tipX + length * Math.cos(t - fan)), (float) (tipY + length * Math.sin(t - fan)));
        gp.lineTo((float) tipX, (float) tipY);
        gp.lineTo((float) (tipX + length * Math.cos(t + fan)), (float) (tipY + length * Math.sin(t + fan)));
        gp.closePath();
        gr.draw(gp, true);
    }

    /** Half the angle of an arrow head, in degrees. */
    private static final int highFan = 16;

    /** Returns a DOT representation of this edge. */
    @Override
    public String toString() {
        String rgb = Integer.toHexString(color.getRGB() & 0xFFFFFF);
        while (rgb.length() < 6)
            rgb = "0" + rgb;
        return "\"" + a.dotName() + "\" -> \"" + b.dotName() + "\" [uuid = \"" + (uuid == null ? "" : esc(uuid.toString())) + "\", color = \"#" + rgb + "\", fontcolor = \"#" + rgb + "\", style = \"" + style.getDotText() + "\", label = \"" + esc(label) + "\", dir = \"" + (ahead && bhead ? "both" : (bhead ? "forward" : "back")) + "\", constraint = \"false\"]\n";
    }
}
