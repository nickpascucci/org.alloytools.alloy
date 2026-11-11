package edu.mit.csail.sdg.alloy4viz;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.mit.csail.sdg.alloy4.ErrorFatal;
import edu.mit.csail.sdg.alloy4graph.LayoutTemplate;

/**
 * Keeps the graphs of related instances visually consistent: all states of one
 * trace share one arrangement, and the next solution of a command leans towards
 * the arrangement of the solution before it.
 * <p>
 * <b>Thread Safety:</b> Can be called only by the AWT event thread.
 */
public final class Continuity {

    /** The states of the current solution. */
    private List<AlloyInstance>                                states   = Collections.emptyList();

    /** The states of the solution before this one; empty if there is none. */
    private List<AlloyInstance>                                earlierStates = Collections.emptyList();

    /** Whether graphs are marked with what changed. */
    private boolean                                            showChanges = true;

    /** For each projection, the arrangement the solution before this one had. */
    private Map<AlloyProjection,LayoutTemplate>                earlier  = new HashMap<AlloyProjection,LayoutTemplate>();

    /** For each projection, the arrangement shared by the states of a trace. */
    private final Map<AlloyProjection,LayoutTemplate>          shared   = new HashMap<AlloyProjection,LayoutTemplate>();

    /** The contexts handed out so far, per projection and state. */
    private final Map<AlloyProjection,Map<Integer,GraphContext>> contexts = new HashMap<AlloyProjection,Map<Integer,GraphContext>>();

    /** The theme that the cached templates and contexts were made for. */
    private VizState                                           theme    = null;
    private int                                                themeVersion;

    /** Starts over with the states of a solution unrelated to anything before. */
    public void start(List<AlloyInstance> states) {
        this.states = states;
        earlierStates = Collections.emptyList();
        earlier = new HashMap<AlloyProjection,LayoutTemplate>();
        forget();
    }

    /**
     * Moves on to the states of the next solution of the same command.
     *
     * @param shown for each projection that has been on screen, the arrangement of
     *            the graph shown for the solution being left
     */
    public void advance(List<AlloyInstance> states, Map<AlloyProjection,LayoutTemplate> shown) {
        // A trace has its shared arrangement, which covers the atoms of all its
        // states; what is on screen covers only the atoms of the states shown.
        earlier = new HashMap<AlloyProjection,LayoutTemplate>(isTrace() ? shared : shown);
        earlierStates = this.states;
        this.states = states;
        forget();
    }

    private void forget() {
        shared.clear();
        contexts.clear();
    }

    private boolean isTrace() {
        return !states.isEmpty() && !states.get(0).isMetamodel && states.get(0).originalA4.getMaxTrace() >= 0;
    }

    /**
     * Sets whether graphs are marked with what changed: each state of a trace
     * against the state before it, and the first state of a solution against the
     * first state of the solution before it.
     */
    public void setShowChanges(boolean showChanges) {
        if (this.showChanges != showChanges)
            contexts.clear();
        this.showChanges = showChanges;
    }

    /** Returns whether graphs are marked with what changed. */
    public boolean showChanges() {
        return showChanges;
    }

    /**
     * Returns the instance that the given state is compared with, or null if there
     * is none.
     */
    private AlloyInstance baseline(int index) {
        if (!showChanges)
            return null;
        if (index > 0 && isTrace())
            return state(index - 1);
        return earlierStates.isEmpty() ? null : earlierStates.get(0);
    }

    /** Returns the number of states: the length of the trace prefix, or 1. */
    public int size() {
        return states.size();
    }

    /**
     * Returns the given state, where indices past the end of the prefix follow the
     * loop of the trace.
     */
    public AlloyInstance state(int index) {
        return states.get(fold(index));
    }

    private int fold(int index) {
        final int n = states.size();
        if (index < n)
            return index < 0 ? 0 : index;
        final int loop = Math.max(0, Math.min(n - 1, states.get(0).originalA4.getLoopState()));
        return loop + (index - loop) % (n - loop);
    }

    /**
     * Returns the context for drawing the given state. The same object comes back
     * for as long as the result would be the same.
     *
     * @param view the theme; all states are drawn with the same one
     */
    public GraphContext contextFor(int index, VizState view, AlloyProjection proj) {
        if (theme != view || themeVersion != view.themeVersion()) {
            forget();
            theme = view;
            themeVersion = view.themeVersion();
        }
        Map<Integer,GraphContext> perState = contexts.get(proj);
        if (perState == null)
            contexts.put(proj, perState = new HashMap<Integer,GraphContext>());
        GraphContext ans = perState.get(index);
        if (ans == null)
            perState.put(index, ans = makeContext(index, view, proj));
        return ans;
    }

    private GraphContext makeContext(int index, VizState view, AlloyProjection proj) {
        if (!isTrace())
            return new GraphContext(earlier.get(proj), false, baseline(index));
        LayoutTemplate template = shared.get(proj);
        if (template == null)
            try {
                shared.put(proj, template = TraceLayout.template(states, view, proj, earlier.get(proj)));
            } catch (ErrorFatal ex) {
                return new GraphContext(null, false, baseline(index));
            }
        return new GraphContext(template, true, baseline(index));
    }

    /**
     * Returns the arrangement that the solution before this one had under the
     * given projection, or null if there is none.
     */
    LayoutTemplate earlierTemplate(AlloyProjection proj) {
        return earlier.get(proj);
    }
}
