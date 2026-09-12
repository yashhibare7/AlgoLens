package com.algolens.trace;

/**
 * Counters accumulated over the whole run. These are what let the UI say "312 comparisons,
 * 47 swaps" next to a claimed O(n^2), which is far more convincing to a student than the
 * complexity label on its own.
 *
 * <p>{@code comparisons} counts comparisons of <em>data</em> -- an array cell or an object
 * reference on at least one side. A loop bound such as {@code i < arr.length} is a condition,
 * not a comparison of the data, so it is excluded. That is what makes the number directly
 * comparable to a textbook analysis.
 */
public record TraceMetrics(
        long statements,
        long comparisons,
        long swaps,
        long arrayReads,
        long arrayWrites,
        long fieldReads,
        long fieldWrites,
        long objectsCreated,
        long calls,
        int maxCallDepth) {

    public static TraceMetrics empty() {
        return new TraceMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
