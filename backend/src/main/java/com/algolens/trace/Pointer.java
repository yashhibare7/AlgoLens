package com.algolens.trace;

/**
 * A variable rendered as a marker on the structure it points into: the {@code i}/{@code j}/
 * {@code mid} arrows under an array, or the {@code head}/{@code slow}/{@code fast} labels above
 * a linked-list node.
 *
 * <p>Exactly one of {@code index} and {@code nodeId} is set, because an array cell is addressed
 * by position and an object by identity. Nulls are omitted from the JSON, so the frontend sees
 * only the field that applies.
 */
public record Pointer(String name, Integer index, String nodeId) {

    /** A pointer into an array, addressed by position. */
    public static Pointer atIndex(String name, int index) {
        return new Pointer(name, index, null);
    }

    /** A pointer at an object, addressed by identity. */
    public static Pointer atNode(String name, String nodeId) {
        return new Pointer(name, null, nodeId);
    }
}
