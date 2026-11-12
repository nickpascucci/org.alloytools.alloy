package edu.mit.csail.sdg.alloy4viz;

import java.util.HashMap;
import java.util.Map;

import edu.mit.csail.sdg.alloy4graph.DotShape;

/**
 * Guesses, from the name of a sig, the everyday thing it stands for, so that
 * its atoms can be drawn in the shape of that thing.
 */
final class DomainShapes {

    private DomainShapes() {}

    /** The words that suggest each shape, in lower case and singular. */
    private static final Map<String,DotShape> WORDS = new HashMap<String,DotShape>();

    private static void add(DotShape shape, String... words) {
        for (String w : words)
            WORDS.put(w, shape);
    }

    static {
        add(DotShape.FOLDER, "dir", "directory", "folder");
        add(DotShape.DOCUMENT, "file", "doc", "document", "page");
        add(DotShape.ENVELOPE, "msg", "message", "mail", "email", "letter", "envelope", "packet");
        add(DotShape.MAILBOX, "mailbox", "inbox", "outbox");
        add(DotShape.LOCK, "lock", "padlock", "mutex");
        add(DotShape.KEY, "key", "password", "credential");
        add(DotShape.PERSON, "person", "user", "human", "man", "woman", "student", "teacher", "employee", "customer", "guest", "member");
        add(DotShape.DATABASE, "db", "database", "datastore", "disk", "storage");
    }

    /** Returns the shape for the given lower case word or its singular, or null. */
    private static DotShape lookup(String word) {
        DotShape ans = WORDS.get(word);
        if (ans == null && word.endsWith("s"))
            ans = WORDS.get(word.substring(0, word.length() - 1));
        return ans;
    }

    /**
     * Returns the shape that suits a sig of the given name, or null if the name
     * suggests none. The module path is ignored; then the name as a whole decides,
     * or else its last word, as in "RootDir" or "room_key".
     */
    static DotShape forName(String name) {
        name = name.substring(name.lastIndexOf('/') + 1);
        DotShape ans = lookup(name.toLowerCase());
        if (ans != null)
            return ans;
        int start = name.length();
        while (start > 0 && Character.isDigit(name.charAt(start - 1)))
            start--;
        final int end = start;
        while (start > 0 && Character.isLowerCase(name.charAt(start - 1)))
            start--;
        if (start > 0 && Character.isUpperCase(name.charAt(start - 1)))
            start--;
        return start == 0 ? null : lookup(name.substring(start, end).toLowerCase());
    }
}
