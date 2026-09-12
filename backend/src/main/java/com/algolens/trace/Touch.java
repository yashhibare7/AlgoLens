package com.algolens.trace;

/**
 * A single cell interaction during one step: "step 12 read cell 3 of {@code arr}, value 8".
 *
 * <p>Touches are the raw material behind two things: the element highlighting the frontend
 * renders, and the swap-collapsing pass in {@link SwapDetector}. Keeping them explicit rather
 * than pre-baking colours means the frontend can restyle without a backend change, and carrying
 * {@code value} lets the swap detector verify that the values really did exchange instead of
 * matching on index shape alone.
 */
public record Touch(String target, int index, TouchKind kind, Object value) {

    public static Touch read(String target, int index, Object value) {
        return new Touch(target, index, TouchKind.READ, value);
    }

    public static Touch write(String target, int index, Object value) {
        return new Touch(target, index, TouchKind.WRITE, value);
    }

    public static Touch compare(String target, int index, Object value) {
        return new Touch(target, index, TouchKind.COMPARE, value);
    }
}
