package com.algolens.execution.interpreter;

import java.util.Arrays;

/**
 * Runtime representation of a Java array.
 *
 * <p>Values are boxed in an {@code Object[]} regardless of element type. That costs memory the
 * real JVM would not spend, but it keeps one representation for {@code int[]}, {@code double[]},
 * {@code String[]} and nested {@code int[][]} -- and array sizes here are bounded by
 * {@code algolens.execution.max-array-length} anyway.
 *
 * <p>{@code name} is the identifier the array was first bound to. Trace events refer to arrays by
 * that name, so it is what the frontend labels the visualization with. Arrays passed into a
 * method keep the caller's name, which is what makes {@code swap(arr, i, j)} render against the
 * same picture the caller sees.
 */
public final class ArrayValue {

    private final String elementType;
    private final Object[] values;
    private String name;

    public ArrayValue(String elementType, Object[] values) {
        this.elementType = elementType;
        this.values = values;
    }

    public static ArrayValue ofSize(String elementType, int length, Object defaultValue) {
        Object[] values = new Object[length];
        Arrays.fill(values, defaultValue);
        return new ArrayValue(elementType, values);
    }

    public String elementType() {
        return elementType;
    }

    public int length() {
        return values.length;
    }

    public Object[] raw() {
        return values;
    }

    public Object get(int index) {
        return values[index];
    }

    public void set(int index, Object value) {
        values[index] = value;
    }

    public String name() {
        return name == null ? "array" : name;
    }

    public boolean hasName() {
        return name != null;
    }

    /** First binding wins, so a helper method's parameter name never renames the caller's array. */
    public void nameIfUnnamed(String candidate) {
        if (name == null) {
            name = candidate;
        }
    }

    public boolean isNested() {
        return elementType.endsWith("[]");
    }

    /**
     * Java-style rendering, used for {@code Arrays.toString} and string concatenation.
     *
     * <p>Delegates to {@link Values#render} so the depth limit applies here too: an array can
     * hold objects that point back at it, and an unbounded renderer would loop forever.
     */
    public String render() {
        return Values.format(this);
    }

    @Override
    public String toString() {
        return render();
    }
}
