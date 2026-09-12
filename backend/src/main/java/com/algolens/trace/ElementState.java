package com.algolens.trace;

/** Per-cell highlight role. Drives colour in the frontend visualizers. */
public enum ElementState {
    DEFAULT,
    ACTIVE,
    COMPARING,
    SWAPPING,
    WRITTEN,
    SORTED,
    PIVOT,
    FOUND,
    EXCLUDED
}
