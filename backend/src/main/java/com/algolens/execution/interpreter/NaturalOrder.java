package com.algolens.execution.interpreter;

import java.util.Comparator;

/**
 * Ordering for {@code TreeMap}, {@code TreeSet} and {@code PriorityQueue}.
 *
 * <p>Only values that are genuinely {@code Comparable} in Java are ordered: numbers, characters,
 * strings and booleans. A user-declared class would need to implement {@code Comparable}, which
 * the subset has no interfaces for, so it is rejected with a message saying so rather than being
 * silently ordered by something arbitrary like allocation id.
 */
public final class NaturalOrder implements Comparator<Object> {

    public static final NaturalOrder INSTANCE = new NaturalOrder();

    private NaturalOrder() {
    }

    @Override
    public int compare(Object left, Object right) {
        if (left == null || right == null) {
            throw new InterpreterException(
                    "NullPointerException: a sorted collection cannot hold null", 0);
        }
        if (Values.isNumeric(left) && Values.isNumeric(right)) {
            if (Values.isFloating(left) || Values.isFloating(right)) {
                return Double.compare(Values.toDouble(left), Values.toDouble(right));
            }
            return Long.compare(Values.toLong(left), Values.toLong(right));
        }
        if (left instanceof String a && right instanceof String b) {
            return a.compareTo(b);
        }
        if (left instanceof Boolean a && right instanceof Boolean b) {
            return Boolean.compare(a, b);
        }
        throw new InterpreterException(
                "A sorted collection needs Comparable values, but got "
                        + Values.typeName(left) + " and " + Values.typeName(right)
                        + ". Interfaces such as Comparable are not in the supported subset yet, "
                        + "so use numbers or strings as the keys.",
                0);
    }
}
