package edu.mit.csail.sdg.alloy4viz;

import edu.mit.csail.sdg.alloy4graph.LayoutTemplate;

/**
 * What the graph of one instance takes from the instances shown around it, so
 * that a sequence of graphs reads as one story.
 */
public final class GraphContext {

    /** The arrangement to follow; null means a fresh layout. */
    public final LayoutTemplate template;

    /**
     * Whether every atom takes its exact place from the template (see
     * {@link edu.mit.csail.sdg.alloy4graph.Graph#layout(LayoutTemplate, boolean)}).
     */
    public final boolean        strict;

    /**
     * The instance whose differences from the drawn one are to be marked; null
     * means that nothing is marked.
     */
    public final AlloyInstance  baseline;

    GraphContext(LayoutTemplate template, boolean strict, AlloyInstance baseline) {
        this.template = template;
        this.strict = strict;
        this.baseline = baseline;
    }
}
