package edu.mit.csail.sdg.alloy4graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable record of where the nodes of a laid-out graph are, keyed by
 * {@link GraphNode#layoutKey()}. Passing it to
 * {@link Graph#layout(LayoutTemplate, boolean)} makes another graph follow the
 * same arrangement.
 */
public final class LayoutTemplate {

    /** The place of one node. */
    static final class Slot {

        /** The layer the node is in; layer 0 is the bottom one. */
        final int layer;
        /** The center of the node. */
        final int x, y;

        /**
         * If the node is a container, the room its own labels take above the graph
         * inside it; otherwise 0.
         */
        final int headW, headH;

        Slot(int layer, int x, int y) {
            this(layer, x, y, 0, 0);
        }

        Slot(int layer, int x, int y, int headW, int headH) {
            this.layer = layer;
            this.x = x;
            this.y = y;
            this.headW = headW;
            this.headH = headH;
        }
    }

    private final Map<String,Slot> slots;

    /** The height of each layer. */
    private final int[]            layerHeights;

    /** The drawing area of the graph the template was taken from. */
    final int                      left, top, width, height;

    /** The template of the graph inside each container, by the key of the container. */
    private final Map<String,LayoutTemplate> inners;

    LayoutTemplate(Map<String,Slot> slots, int[] layerHeights, int left, int top, int width, int height) {
        this(slots, Collections.<String,LayoutTemplate> emptyMap(), layerHeights, left, top, width, height);
    }

    LayoutTemplate(Map<String,Slot> slots, Map<String,LayoutTemplate> inners, int[] layerHeights, int left, int top, int width, int height) {
        this.inners = Collections.unmodifiableMap(new LinkedHashMap<String,LayoutTemplate>(inners));
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<String,Slot>(slots));
        this.layerHeights = layerHeights.clone();
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    /** Returns true if the template has a slot for the given key. */
    public boolean contains(String key) {
        return key != null && slots.containsKey(key);
    }

    /**
     * Returns the template for the graph inside the node with the given key, or
     * null if there is none.
     */
    public LayoutTemplate inner(String key) {
        return key == null ? null : inners.get(key);
    }

    /** Returns the number of slots. */
    public int size() {
        return slots.size();
    }

    /** Returns the slot for the given key, or null if there is none. */
    Slot slot(String key) {
        return key == null ? null : slots.get(key);
    }

    /** Returns the height of the given layer, or 0 if there is no such layer. */
    int layerHeight(int layer) {
        return layer >= 0 && layer < layerHeights.length ? layerHeights[layer] : 0;
    }
}
