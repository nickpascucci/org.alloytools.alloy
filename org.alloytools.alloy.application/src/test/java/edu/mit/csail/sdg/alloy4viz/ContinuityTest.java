package edu.mit.csail.sdg.alloy4viz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.mit.csail.sdg.alloy4graph.DotShape;
import edu.mit.csail.sdg.alloy4graph.Graph;
import edu.mit.csail.sdg.alloy4graph.LayoutTemplate;

public class ContinuityTest {

    private static List<AlloyInstance> trace, fixed;

    @BeforeClass
    public static void solve() throws Exception {
        trace = TraceFixture.states(TraceFixture.TRASH);
        fixed = TraceFixture.states(TraceFixture.STATIC);
    }

    @Test
    public void statesPastThePrefixFollowTheLoop() {
        Continuity c = new Continuity();
        c.start(trace);
        int n = trace.size(), loop = trace.get(0).originalA4.getLoopState();
        assertEquals(n, c.size());
        assertSame(trace.get(2), c.state(2));
        assertSame(trace.get(loop), c.state(n));
        assertSame(trace.get(loop), c.state(n + (n - loop)));
    }

    @Test
    public void allStatesOfATraceShareOneStrictTemplate() {
        Continuity c = new Continuity();
        c.start(trace);
        VizState view = new VizState(trace.get(0));
        GraphContext first = c.contextFor(0, view, new AlloyProjection());
        assertTrue(first.strict);
        assertNotNull(first.template);
        assertTrue(first.template.contains("Root$0"));
        assertSame(first, c.contextFor(0, view, new AlloyProjection()));
        assertSame(first.template, c.contextFor(2, view, new AlloyProjection()).template);
        assertSame(first.template, c.contextFor(trace.size() + 1, view, new AlloyProjection()).template);
    }

    @Test
    public void aThemeChangeGivesANewTemplate() {
        Continuity c = new Continuity();
        c.start(trace);
        VizState view = new VizState(trace.get(0));
        LayoutTemplate before = c.contextFor(0, view, new AlloyProjection()).template;
        view.shape.put(null, DotShape.CIRCLE);
        assertNotSame(before, c.contextFor(0, view, new AlloyProjection()).template);
    }

    @Test
    public void aStaticInstanceStartsWithoutATemplate() {
        Continuity c = new Continuity();
        c.start(fixed);
        VizState view = new VizState(fixed.get(0));
        GraphContext ctx = c.contextFor(0, view, new AlloyProjection());
        assertNull(ctx.template);
        assertFalse(ctx.strict);
    }

    @Test
    public void theNextStaticInstanceLeansOnWhatWasShown() throws Exception {
        Continuity c = new Continuity();
        c.start(fixed);
        VizState view = new VizState(fixed.get(0));
        Graph shown = StaticGraphMaker.buildGraph(fixed.get(0), view, null);
        shown.layout();
        LayoutTemplate t = shown.captureTemplate();

        c.advance(fixed, Collections.singletonMap(new AlloyProjection(), t));

        GraphContext ctx = c.contextFor(0, view, new AlloyProjection());
        assertSame(t, ctx.template);
        assertFalse(ctx.strict);
    }

    @Test
    public void theNextTraceLeansOnTheTraceBeforeIt() {
        Continuity c = new Continuity();
        c.start(trace);
        VizState view = new VizState(trace.get(0));
        LayoutTemplate before = c.contextFor(0, view, new AlloyProjection()).template;

        c.advance(trace, Collections.<AlloyProjection,LayoutTemplate> emptyMap());

        GraphContext ctx = c.contextFor(0, view, new AlloyProjection());
        assertTrue(ctx.strict);
        assertNotSame(before, ctx.template);
        assertSame(before, c.earlierTemplate(new AlloyProjection()));
    }

    @Test
    public void startingOverForgetsTheEarlierSolution() {
        Continuity c = new Continuity();
        c.start(trace);
        c.contextFor(0, new VizState(trace.get(0)), new AlloyProjection());
        c.advance(trace, Collections.<AlloyProjection,LayoutTemplate> emptyMap());
        c.start(fixed);
        assertNull(c.earlierTemplate(new AlloyProjection()));
    }
}
