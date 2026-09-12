package com.algolens.execution.interpreter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code HashSet}, {@code LinkedHashSet} and {@code TreeSet}.
 *
 * <p>As with {@link MapValue}, a {@code HashSet} iterates in insertion order here so that a
 * stored trace replays identically. Java leaves that order unspecified, so nothing guaranteed is
 * being broken.
 */
public final class SetValue {

    public enum Kind {
        HASH_SET("HashSet"),
        LINKED_HASH_SET("LinkedHashSet"),
        TREE_SET("TreeSet");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final Kind kind;
    private final Set<Object> elements;
    private String name;

    public SetValue(Kind kind) {
        this.kind = kind;
        this.elements = kind == Kind.TREE_SET
                ? new TreeSet<>(NaturalOrder.INSTANCE)
                : new LinkedHashSet<>();
    }

    public Kind kind() {
        return kind;
    }

    public Set<Object> elements() {
        return elements;
    }

    public int size() {
        return elements.size();
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

    public int indexOf(Object value) {
        int index = 0;
        for (Object existing : elements) {
            if (java.util.Objects.equals(existing, value)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public String render() {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (Object element : elements) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(Values.shallow(element));
        }
        return out.append(']').toString();
    }

    @Override
    public String toString() {
        return render();
    }
}
