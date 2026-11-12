package edu.mit.csail.sdg.alloy4viz;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable; tells which atoms of an instance are drawn inside which other
 * atom, according to the relations that the theme shows as containers.
 * <p>
 * A tuple of such a relation puts its last atom inside its first atom (the
 * other way around if the relation is laid out backwards). That only works
 * when the atom has a single place to be: an atom that such tuples would put
 * inside two atoms, or inside itself directly or through other atoms, is
 * inside none, and its tuples are left to be drawn as arcs.
 */
final class Containment {

    /** Maps each atom that is inside another atom to that other atom. */
    private final Map<AlloyAtom,AlloyAtom>                    parent = new LinkedHashMap<AlloyAtom,AlloyAtom>();

    /**
     * Maps each atom that is inside another atom to the tuples that say so, each
     * under its relation.
     */
    private final Map<AlloyAtom,Map<AlloyRelation,AlloyTuple>> reason = new LinkedHashMap<AlloyAtom,Map<AlloyRelation,AlloyTuple>>();

    /** Maps the original name of each atom that is inside another atom to the name of that atom. */
    private final Map<String,String>                          byName = new LinkedHashMap<String,String>();

    /** Returns true if the theme shows the given relation of the given model as a container. */
    static boolean applies(VizState view, AlloyRelation rel) {
        return rel.getArity() == 2 && view.container.resolve(rel) && !(view.hidePrivate() && rel.isPrivate) && !(view.hideSkolem() && rel.isSkolem);
    }

    /**
     * Works out the containment in the given instance, which is already projected.
     *
     * @param canShow tells whether an atom gets a node at all
     */
    Containment(AlloyInstance instance, VizState view, Predicate<AlloyAtom> canShow) {
        final Set<AlloyAtom> torn = new HashSet<AlloyAtom>();
        for (AlloyRelation rel : instance.model.getRelations())
            if (applies(view, rel)) {
                final boolean back = view.layoutBack.resolve(rel);
                for (AlloyTuple tuple : instance.relation2tuples(rel)) {
                    AlloyAtom outer = back ? tuple.getEnd() : tuple.getStart(), inner = back ? tuple.getStart() : tuple.getEnd();
                    if (!canShow.test(outer) || !canShow.test(inner))
                        continue;
                    // Several tuples may put an atom inside the same atom, but not inside different ones.
                    AlloyAtom old = parent.put(inner, outer);
                    if (old != null && !old.equals(outer))
                        torn.add(inner);
                    Map<AlloyRelation,AlloyTuple> why = reason.get(inner);
                    if (why == null)
                        reason.put(inner, why = new LinkedHashMap<AlloyRelation,AlloyTuple>());
                    why.put(rel, tuple);
                }
            }
        parent.keySet().removeAll(torn);
        // Whoever is inside itself, directly or through other atoms, is inside none.
        final Set<AlloyAtom> looping = new HashSet<AlloyAtom>();
        for (AlloyAtom start : parent.keySet()) {
            List<AlloyAtom> walk = new ArrayList<AlloyAtom>();
            for (AlloyAtom a = start; a != null && !looping.contains(a); a = parent.get(a)) {
                int at = walk.indexOf(a);
                if (at >= 0) {
                    looping.addAll(walk.subList(at, walk.size()));
                    break;
                }
                walk.add(a);
            }
        }
        parent.keySet().removeAll(looping);
        reason.keySet().retainAll(parent.keySet());
        for (Map.Entry<AlloyAtom,AlloyAtom> e : parent.entrySet())
            byName.put(e.getKey().getOriginalName(), e.getValue().getOriginalName());
    }

    /** Returns the atom that the given atom is inside of, or null if there is none. */
    AlloyAtom parentOf(AlloyAtom atom) {
        return parent.get(atom);
    }

    /**
     * Returns the original name of the atom that the atom of the given original
     * name is inside of, or null if there is none.
     */
    String parentOf(String atomName) {
        return byName.get(atomName);
    }

    /** Returns the atoms that are inside another atom. */
    Set<AlloyAtom> nested() {
        return parent.keySet();
    }

    /** Returns true if the given tuple of the given relation is what puts an atom inside another. */
    boolean nests(AlloyRelation rel, AlloyTuple tuple) {
        for (AlloyAtom end : new AlloyAtom[] {
                                              tuple.getStart(), tuple.getEnd()
        })
            if (reason.containsKey(end) && tuple.equals(reason.get(end).get(rel)))
                return true;
        return false;
    }
}
