package com.algolens.execution.interpreter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One lexical scope. Scopes chain to their enclosing scope inside the same call frame; the
 * interpreter falls back to a separate globals environment for static fields, so a method can
 * never see its caller's locals.
 *
 * <p>Insertion order is preserved because the variables panel reads better when variables appear
 * in the order the code declares them rather than alphabetically.
 */
public final class Environment {

    /** A declared variable: its static type plus its current value. */
    public static final class Slot {
        private final String declaredType;
        private Object value;

        Slot(String declaredType, Object value) {
            this.declaredType = declaredType;
            this.value = value;
        }

        public String declaredType() {
            return declaredType;
        }

        public Object value() {
            return value;
        }

        public void set(Object newValue) {
            this.value = newValue;
        }
    }

    private final Environment enclosing;
    private final Map<String, Slot> slots = new LinkedHashMap<>();

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    public Environment enclosing() {
        return enclosing;
    }

    public void declare(String name, String declaredType, Object value) {
        if (slots.containsKey(name)) {
            throw new InterpreterException("Variable '" + name + "' is already defined", 0);
        }
        slots.put(name, new Slot(declaredType, value));
    }

    public Slot lookup(String name) {
        for (Environment scope = this; scope != null; scope = scope.enclosing) {
            Slot slot = scope.slots.get(name);
            if (slot != null) {
                return slot;
            }
        }
        return null;
    }

    /** Every visible variable, innermost shadowing outermost. */
    public void collectVisible(Map<String, Slot> into) {
        if (enclosing != null) {
            enclosing.collectVisible(into);
        }
        into.putAll(slots);
    }

    public Map<String, Slot> own() {
        return slots;
    }
}
