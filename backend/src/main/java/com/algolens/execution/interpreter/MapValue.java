package com.algolens.execution.interpreter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code HashMap}, {@code LinkedHashMap} and {@code TreeMap}.
 *
 * <p>Keys are the interpreted values themselves, so key equality is Java's: two boxed
 * {@code Integer}s with the same value match, and two distinct objects do not (the subset has no
 * {@code equals} override). That means {@code map.get(1)} after {@code map.put(1, x)} works, and
 * {@code map.get(1L)} does not -- exactly as in real Java.
 *
 * <p><b>Deliberate determinism:</b> a real {@code HashMap} has an unspecified iteration order.
 * A trace has to replay identically when reopened from history days later, so a {@code HashMap}
 * here iterates in insertion order. Nothing is promised by Java that this breaks, and insertion
 * order is far easier to follow on screen.
 */
public final class MapValue {

    public enum Kind {
        HASH_MAP("HashMap"),
        LINKED_HASH_MAP("LinkedHashMap"),
        TREE_MAP("TreeMap");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final Kind kind;
    private final Map<Object, Object> entries;
    private String name;

    public MapValue(Kind kind) {
        this.kind = kind;
        this.entries = kind == Kind.TREE_MAP
                ? new TreeMap<>(NaturalOrder.INSTANCE)
                : new LinkedHashMap<>();
    }

    public Kind kind() {
        return kind;
    }

    public Map<Object, Object> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public String name() {
        return name == null ? kind.displayName().toLowerCase() : name;
    }

    public boolean hasName() {
        return name != null;
    }

    public void nameIfUnnamed(String candidate) {
        if (name == null) {
            name = candidate;
        }
    }

    /** Insertion (or sorted) position of a key, used to address an entry for highlighting. */
    public int indexOfKey(Object key) {
        int index = 0;
        for (Object existing : entries.keySet()) {
            if (java.util.Objects.equals(existing, key)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public String render() {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(Values.shallow(entry.getKey())).append('=')
                    .append(Values.shallow(entry.getValue()));
        }
        return out.append('}').toString();
    }

    @Override
    public String toString() {
        return render();
    }
}
