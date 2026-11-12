package edu.mit.csail.sdg.alloy4viz;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The differences between two instances of one model. Atoms are matched by
 * their original name, which is exact between the states of a trace; between
 * two solutions it is as good as the solver's naming.
 */
public final class InstanceDiff {

    /** What one instance holds, in terms of names. */
    private static final class Content {

        /** Maps the name of each atom to the names of the sets it is in. */
        final Map<String,Set<String>> atoms  = new LinkedHashMap<String,Set<String>>();
        /** The keys of all tuples. */
        final Set<String>             tuples = new TreeSet<String>();
        /** Maps each relation and start atom to the keys of the tuples starting there. */
        final Map<String,Set<String>> from   = new LinkedHashMap<String,Set<String>>();

        Content(AlloyInstance inst) {
            for (AlloyAtom atom : inst.getAllAtoms()) {
                Set<String> sets = new TreeSet<String>();
                for (AlloySet set : inst.atom2sets(atom))
                    sets.add(set.getName());
                atoms.put(atom.getOriginalName(), sets);
            }
            for (AlloyRelation rel : inst.model.getRelations())
                for (AlloyTuple tuple : inst.relation2tuples(rel)) {
                    String key = key(rel, tuple), start = key(rel) + tuple.getStart().getOriginalName();
                    tuples.add(key);
                    Set<String> set = from.get(start);
                    if (set == null)
                        from.put(start, set = new TreeSet<String>());
                    set.add(key);
                }
        }
    }

    private final Content     before, after;
    private final Set<String> added   = new TreeSet<String>();
    private final Set<String> removed = new TreeSet<String>();
    private final Set<String> moved   = new TreeSet<String>();

    private InstanceDiff(AlloyInstance before, AlloyInstance after) {
        this.before = new Content(before);
        this.after = new Content(after);
        for (Map.Entry<String,Set<String>> e : this.after.atoms.entrySet()) {
            Set<String> old = this.before.atoms.get(e.getKey());
            if (old == null)
                added.add(e.getKey());
            else if (!old.equals(e.getValue()))
                moved.add(e.getKey());
        }
        for (String name : this.before.atoms.keySet())
            if (!this.after.atoms.containsKey(name))
                removed.add(name);
    }

    private static String key(AlloyRelation rel) {
        return rel.getName() + "/" + rel.getArity() + ":";
    }

    private static String key(AlloyRelation rel, AlloyTuple tuple) {
        StringBuilder sb = new StringBuilder(key(rel));
        for (AlloyAtom atom : tuple.getAtoms())
            sb.append(atom.getOriginalName()).append("->");
        return sb.toString();
    }

    /** Computes what changed going from one instance to the other. */
    public static InstanceDiff between(AlloyInstance before, AlloyInstance after) {
        return new InstanceDiff(before, after);
    }

    /** Returns the names of the atoms that are only in the later instance. */
    public Set<String> addedAtoms() {
        return Collections.unmodifiableSet(added);
    }

    /** Returns the names of the atoms that are only in the earlier instance. */
    public Set<String> removedAtoms() {
        return Collections.unmodifiableSet(removed);
    }

    /**
     * Returns the names of the atoms that are in both instances but in different
     * sets.
     */
    public Set<String> movedAtoms() {
        return Collections.unmodifiableSet(moved);
    }

    /** Returns true if the tuple is in the relation only in the later instance. */
    public boolean isAdded(AlloyRelation rel, AlloyTuple tuple) {
        String key = key(rel, tuple);
        return after.tuples.contains(key) && !before.tuples.contains(key);
    }

    /** Returns true if the tuple is in the relation only in the earlier instance. */
    public boolean isRemoved(AlloyRelation rel, AlloyTuple tuple) {
        String key = key(rel, tuple);
        return before.tuples.contains(key) && !after.tuples.contains(key);
    }

    /**
     * Returns true if the tuples of the relation that start at the named atom are
     * not the same in both instances.
     */
    public boolean differsFrom(AlloyRelation rel, String atomName) {
        Set<String> a = before.from.get(key(rel) + atomName), b = after.from.get(key(rel) + atomName);
        return a == null ? b != null : !a.equals(b);
    }

    /** Returns true if the two instances have the same atoms, sets and tuples. */
    public boolean isEmpty() {
        return before.atoms.equals(after.atoms) && before.tuples.equals(after.tuples);
    }
}
