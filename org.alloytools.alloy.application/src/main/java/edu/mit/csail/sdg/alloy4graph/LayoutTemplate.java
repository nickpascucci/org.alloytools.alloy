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

        Slot(int layer, int x, int y) {
            this.layer = layer;
            this.x = x;
            this.y = y;
        }
    }

    private final Map<String,Slot> slots;

    /** The height of each layer. */
    private final int[]            layerHeights;

    /** The drawing area of the graph the template was taken from. */
    final int                      left, top, width, height;

    LayoutTemplate(Map<String,Slot> slots, int[] layerHeights, int left, int top, int width, int height) {
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
