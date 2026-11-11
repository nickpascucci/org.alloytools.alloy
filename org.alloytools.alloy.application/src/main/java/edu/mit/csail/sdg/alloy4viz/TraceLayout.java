package edu.mit.csail.sdg.alloy4viz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.mit.csail.sdg.alloy4.ErrorFatal;
import edu.mit.csail.sdg.alloy4graph.Graph;
import edu.mit.csail.sdg.alloy4graph.LayoutTemplate;

/**
 * Computes one arrangement for all the states of a trace, so that drawing each
 * state onto it shows every atom in the same place throughout.
 * <p>
 * This is the "foresighted layout" of Diehl and Goerg (Graphs, They Are
 * Changing, Graph Drawing 2002): the graph of the union of all states is laid
 * out once, and each state takes its places from it.
 */
public final class TraceLayout {

    private TraceLayout() {}

    /**
     * Returns the instance that has every atom, every set membership and every
     * tuple that occurs in at least one of the given states (which must not be
     * empty). Atoms are told apart by their original name.
     */
    public static AlloyInstance union(List<AlloyInstance> states) {
        final Map<String,AlloyAtom> byName = new LinkedHashMap<String,AlloyAtom>();
        final Map<AlloyAtom,Set<AlloySet>> atom2sets = new LinkedHashMap<AlloyAtom,Set<AlloySet>>();
        final Map<AlloyRelation,Set<AlloyTuple>> rel2tuples = new LinkedHashMap<AlloyRelation,Set<AlloyTuple>>();
        for (AlloyInstance state : states) {
            for (AlloyAtom atom : state.getAllAtoms()) {
                AlloyAtom same = byName.get(atom.getOriginalName());
                if (same == null) {
                    byName.put(atom.getOriginalName(), same = atom);
                    atom2sets.put(same, new LinkedHashSet<AlloySet>());
                }
                atom2sets.get(same).addAll(state.atom2sets(atom));
            }
            for (AlloyRelation rel : state.model.getRelations()) {
                Set<AlloyTuple> tuples = rel2tuples.get(rel);
                if (tuples == null)
                    rel2tuples.put(rel, tuples = new LinkedHashSet<AlloyTuple>());
                for (AlloyTuple tuple : state.relation2tuples(rel)) {
                    List<AlloyAtom> atoms = new ArrayList<AlloyAtom>(tuple.getArity());
                    for (AlloyAtom atom : tuple.getAtoms())
                        atoms.add(byName.get(atom.getOriginalName()));
                    tuples.add(new AlloyTuple(atoms));
                }
            }
        }
        final AlloyInstance first = states.get(0);
        return new AlloyInstance(first.originalA4, first.filename, first.commandname, first.model, atom2sets, rel2tuples, first.isMetamodel, first.tracelen, first.looplen);
    }

    /**
     * Returns the template that places every atom of the given states.
     *
     * @param earlier if not null, the arrangement leans towards this one
     */
    public static LayoutTemplate template(List<AlloyInstance> states, VizState view, AlloyProjection proj, LayoutTemplate earlier) throws ErrorFatal {
        Graph graph = StaticGraphMaker.buildGraph(union(states), view, proj);
        graph.layout(earlier, false);
        return graph.captureTemplate();
    }
}
