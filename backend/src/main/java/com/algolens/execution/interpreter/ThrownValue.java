package com.algolens.execution.interpreter;

/**
 * An exception instance, either created by {@code throw new ...} or raised by the interpreter
 * itself (an out-of-bounds index, a division by zero).
 *
 * <p>Both come through this one type so that {@code catch (Exception e)} works uniformly: code
 * that guards against a bad index behaves the same whether the index came from user arithmetic
 * or from a literal.
 *
 * <p>{@code type} is a class name string rather than a class hierarchy, because the subset has
 * no inheritance. {@link #matches} encodes the small part of the real hierarchy that matters --
 * {@code Exception} and {@code RuntimeException} catch everything below them.
 */
public record ThrownValue(String type, String message) {

    /** The catch-all names. Anything else must match exactly. */
    private static final java.util.Set<String> CATCH_ALL =
            java.util.Set.of("Exception", "RuntimeException", "Throwable", "Error");

    public boolean matches(String caughtType) {
        return CATCH_ALL.contains(caughtType) || caughtType.equals(type);
    }

    public String render() {
        return message == null || message.isBlank() ? type : type + ": " + message;
    }
}
