package com.algolens.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Recognises the three-statement temp-variable swap and labels it as one logical SWAP.
 *
 * <pre>
 *   int temp = arr[j];        // reads  arr[j]      -> value v1
 *   arr[j] = arr[j + 1];      // reads  arr[j+1]    -> value v2, writes arr[j] = v2
 *   arr[j + 1] = temp;        // writes arr[j+1]    -> value v1
 * </pre>
 *
 * <p>Nothing anywhere else in the pipeline is allowed to guess at algorithm shapes -- this is the
 * single, deliberately narrow exception, because "swap" is the one composite operation students
 * actually think in. The individual statements still each get their own step, so stepping through
 * the code keeps matching the source exactly; only the third statement is relabelled.
 *
 * <p>The match checks both the index shape <em>and</em> that the values genuinely exchanged, so
 * a lookalike sequence that happens to write an unrelated value is not mislabelled. A swap
 * written some other way (helper method, XOR trick) simply does not match and renders as plain
 * reads and writes: still correct, just less editorialised.
 */
public final class SwapDetector {

    private SwapDetector() {
    }

    public static List<TraceEvent> collapse(List<TraceEvent> events) {
        if (events.size() < 3) {
            return events;
        }
        List<TraceEvent> result = new ArrayList<>(events);
        for (int i = 0; i + 2 < result.size(); i++) {
            Swap swap = match(result.get(i), result.get(i + 1), result.get(i + 2));
            if (swap == null) {
                continue;
            }
            TraceEvent third = result.get(i + 2);
            List<Touch> touches = List.of(
                    new Touch(swap.target(), swap.indexA(), TouchKind.SWAP, swap.valueB()),
                    new Touch(swap.target(), swap.indexB(), TouchKind.SWAP, swap.valueA()));
            String message = "Swapped %s[%d] and %s[%d] -> %s and %s".formatted(
                    swap.target(), swap.indexA(), swap.target(), swap.indexB(),
                    swap.valueB(), swap.valueA());
            TraceEvent relabelled = third.relabelled(TraceAction.SWAP, message)
                    .withTouches(touches, Highlighter.apply(third.visualizations(), touches));
            result.set(i + 2, relabelled);
            i += 2;
        }
        return result;
    }

    /** Number of SWAP-labelled steps in a finished trace, used for metrics. */
    public static long countSwaps(List<TraceEvent> events) {
        return events.stream().filter(e -> e.action() == TraceAction.SWAP).count();
    }

    private static Swap match(TraceEvent first, TraceEvent second, TraceEvent third) {
        Touch readA = sole(first, TouchKind.READ);
        if (readA == null || has(first, TouchKind.WRITE)) {
            return null;
        }
        Touch readB = sole(second, TouchKind.READ);
        Touch writeA = sole(second, TouchKind.WRITE);
        if (readB == null || writeA == null) {
            return null;
        }
        Touch writeB = sole(third, TouchKind.WRITE);
        if (writeB == null || has(third, TouchKind.READ)) {
            return null;
        }

        String target = readA.target();
        boolean sameArray = target.equals(readB.target())
                && target.equals(writeA.target())
                && target.equals(writeB.target());
        boolean indicesMatch = writeA.index() == readA.index()
                && writeB.index() == readB.index()
                && readA.index() != readB.index();
        boolean valuesExchanged = Objects.equals(writeA.value(), readB.value())
                && Objects.equals(writeB.value(), readA.value());

        if (!sameArray || !indicesMatch || !valuesExchanged) {
            return null;
        }
        return new Swap(target, readA.index(), readB.index(), readA.value(), readB.value());
    }

    /** The single touch of {@code kind} in this step, or null when there are zero or many. */
    private static Touch sole(TraceEvent event, TouchKind kind) {
        if (event.touches() == null) {
            return null;
        }
        Touch found = null;
        for (Touch touch : event.touches()) {
            if (touch.kind() != kind) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = touch;
        }
        return found;
    }

    private static boolean has(TraceEvent event, TouchKind kind) {
        return event.touches() != null && event.touches().stream().anyMatch(t -> t.kind() == kind);
    }

    private record Swap(String target, int indexA, int indexB, Object valueA, Object valueB) {
    }
}
