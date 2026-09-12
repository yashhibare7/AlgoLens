package com.algolens.trace;

public record ArrayElement(int index, Object value, ElementState state) {

    public static ArrayElement plain(int index, Object value) {
        return new ArrayElement(index, value, ElementState.DEFAULT);
    }

    public ArrayElement withState(ElementState newState) {
        return new ArrayElement(index, value, newState);
    }
}
