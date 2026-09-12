package com.algolens.execution.interpreter;

import java.util.ArrayList;
import java.util.List;

/**
 * Every ordered, index-addressable collection: {@code ArrayList}, {@code LinkedList},
 * {@code Stack}, {@code ArrayDeque} and {@code PriorityQueue}.
 *
 * <p>One class rather than five because the storage is identical -- an ordered sequence -- and
 * only the <em>method semantics</em> differ. {@link #kind} records which type the user asked for,
 * and {@link CollectionSupport} uses it to dispatch: {@code push} appends on a {@code Stack} but
 * prepends on an {@code ArrayDeque}, and {@code poll} takes the smallest element from a
 * {@code PriorityQueue} but the first from a {@code Queue}. Getting that wrong would make the
 * visualization disagree with the algorithm, so the distinction is kept explicit.
 */
public final class ListValue {

    /** Which declared type this is, because it decides what push/pop/peek mean. */
    public enum Kind {
        ARRAY_LIST("ArrayList"),
        LINKED_LIST("LinkedList"),
        STACK("Stack"),
        ARRAY_DEQUE("ArrayDeque"),
        PRIORITY_QUEUE("PriorityQueue");

        private final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final Kind kind;
    private final List<Object> elements = new ArrayList<>();
    private String name;

    public ListValue(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public List<Object> elements() {
        return elements;
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public Object get(int index) {
        return elements.get(index);
    }

    public String name() {
        return name == null ? kind.displayName().toLowerCase() : name;
    }

    public boolean hasName() {
        return name != null;
    }

    /** First binding wins, so a helper's parameter name never renames the caller's list. */
    public void nameIfUnnamed(String candidate) {
        if (name == null) {
            name = candidate;
        }
    }

    /**
     * Inserts respecting the collection's ordering: a priority queue keeps itself sorted so that
     * index 0 is always the head, which is what makes the visualization of a Dijkstra frontier
     * readable.
     */
    public void addOrdered(Object value) {
        if (kind != Kind.PRIORITY_QUEUE) {
            elements.add(value);
            return;
        }
        int position = 0;
        while (position < elements.size()
                && NaturalOrder.INSTANCE.compare(elements.get(position), value) <= 0) {
            position++;
        }
        elements.add(position, value);
    }

    public String render() {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(Values.shallow(elements.get(i)));
        }
        return out.append(']').toString();
    }

    @Override
    public String toString() {
        return render();
    }
}
