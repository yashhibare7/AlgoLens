package com.algolens.trace;

/**
 * Shapes the frontend knows how to draw. ARRAY and MATRIX ship in the MVP; the rest are
 * declared now so that adding a linked-list or tree executor later is a backend-only change.
 */
public enum VisualizationType {
    ARRAY,
    MATRIX,
    /** A key/value store, drawn as entry rows. */
    MAP,
    /** An unordered collection, drawn as a row of values with no indices. */
    SET,
    LINKED_LIST,
    STACK,
    QUEUE,
    TREE,
    GRAPH
}
