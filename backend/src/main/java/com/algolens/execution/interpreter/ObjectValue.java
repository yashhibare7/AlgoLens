package com.algolens.execution.interpreter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A runtime instance of a user-declared class.
 *
 * <p>Fields are {@link Environment.Slot}s so they carry their declared type and go through the
 * same assignment conversion as locals -- {@code node.data = 3.5} is rejected for an
 * {@code int} field exactly as it would be for an {@code int} local.
 *
 * <p>Identity matters here in a way it does not for primitives. {@code slow == fast} in a cycle
 * detection loop compares references, so equality is object identity and every instance gets a
 * stable {@code id}. That id is also what the trace uses to name nodes, which is what lets the
 * frontend draw the same node in the same place from one step to the next.
 */
public final class ObjectValue {

    private final String className;
    private final int id;
    private final Map<String, Environment.Slot> fields = new LinkedHashMap<>();

    public ObjectValue(String className, int id) {
        this.className = className;
        this.id = id;
    }

    public String className() {
        return className;
    }

    public int id() {
        return id;
    }

    /** Stable identifier used by the trace to name this node, e.g. {@code Node@3}. */
    public String reference() {
        return className + "@" + id;
    }

    public void declareField(String name, String declaredType, Object value) {
        fields.put(name, new Environment.Slot(declaredType, value));
    }

    public Environment.Slot field(String name) {
        return fields.get(name);
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }

    /** Declaration order, which is the order the frontend lists fields in. */
    public Map<String, Environment.Slot> fields() {
        return fields;
    }

    /**
     * A one-level rendering: {@code Node@1{data=1, next=Node@2}}.
     *
     * <p>Deliberately not recursive. A linked list with a cycle -- the exact thing people build
     * to test cycle detection -- would render forever, and even an acyclic list would print the
     * entire tail every time a single node was displayed.
     */
    public String render() {
        StringBuilder out = new StringBuilder(reference()).append('{');
        boolean first = true;
        for (Map.Entry<String, Environment.Slot> entry : fields.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(entry.getKey()).append('=')
                    .append(Values.shallow(entry.getValue().value()));
        }
        return out.append('}').toString();
    }

    @Override
    public String toString() {
        return render();
    }
}
