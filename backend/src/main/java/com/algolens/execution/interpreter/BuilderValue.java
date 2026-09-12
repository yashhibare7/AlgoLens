package com.algolens.execution.interpreter;

/**
 * {@code StringBuilder} and {@code StringBuffer}.
 *
 * <p>Mutable, unlike {@code String}, which is the whole reason it exists in the algorithms
 * people write -- building a result inside a loop. Shown in the variables panel as its current
 * contents rather than as a structure, because that is what a learner wants to watch grow.
 */
public final class BuilderValue {

    private final StringBuilder content = new StringBuilder();

    public BuilderValue() {
    }

    public BuilderValue(String initial) {
        content.append(initial);
    }

    public StringBuilder content() {
        return content;
    }

    public int length() {
        return content.length();
    }

    public String render() {
        return content.toString();
    }

    @Override
    public String toString() {
        return render();
    }
}
