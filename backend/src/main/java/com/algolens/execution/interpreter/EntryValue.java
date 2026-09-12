package com.algolens.execution.interpreter;

/**
 * A {@code Map.Entry}, as produced by {@code map.entrySet()}.
 *
 * <p>A snapshot rather than a live view: {@code setValue} is not supported, so iterating
 * {@code entrySet()} and mutating through the entry is rejected instead of silently doing
 * nothing. Iterating and writing back through {@code map.put} works normally.
 */
public record EntryValue(Object key, Object value) {

    public String render() {
        return Values.shallow(key) + "=" + Values.shallow(value);
    }
}
