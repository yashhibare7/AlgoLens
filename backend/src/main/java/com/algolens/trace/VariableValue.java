package com.algolens.trace;

/**
 * A scalar variable snapshot. {@code changed} is true when this step is the one that
 * modified it, which lets the frontend flash the row instead of the user hunting for the diff.
 */
public record VariableValue(String name, String type, Object value, boolean changed) {

    public static VariableValue of(String name, String type, Object value) {
        return new VariableValue(name, type, value, false);
    }
}
