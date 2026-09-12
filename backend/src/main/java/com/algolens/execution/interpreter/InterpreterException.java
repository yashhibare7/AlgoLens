package com.algolens.execution.interpreter;

import java.util.Set;

/**
 * A runtime failure in interpreted code: bad index, divide by zero, unknown method, ...
 *
 * <p>{@link #exceptionType()} decides whether user code can catch it, and that distinction
 * matters. A genuine Java exception ({@code NullPointerException}, {@code ArithmeticException})
 * is catchable, because guarding against one is normal programming. An interpreter-level refusal
 * ("this syntax is not supported yet") is <em>not</em> catchable: letting
 * {@code catch (Exception e)} swallow it would turn a clear "AlgoLens does not support this"
 * message into silently wrong behaviour, which is far worse than the error itself.
 *
 * <p>The type is recognised from the message rather than passed at every throw site. That is
 * safe because {@link #CATCHABLE} is a closed set of real Java exception names, and interpreter
 * refusals are phrased as prose that cannot match one.
 */
public class InterpreterException extends RuntimeException {

    /** Java exceptions user code is allowed to catch. */
    private static final Set<String> CATCHABLE = Set.of(
            "NullPointerException",
            "ArrayIndexOutOfBoundsException",
            "IndexOutOfBoundsException",
            "StringIndexOutOfBoundsException",
            "ArithmeticException",
            "NumberFormatException",
            "NoSuchElementException",
            "EmptyStackException",
            "ClassCastException",
            "IllegalArgumentException",
            "IllegalStateException",
            "UnsupportedOperationException",
            "NegativeArraySizeException");

    private final int line;
    private final String exceptionType;

    public InterpreterException(String message, int line) {
        super(line > 0 ? "Line " + line + ": " + message : message);
        this.line = line;
        this.exceptionType = deriveType(message);
    }

    /**
     * A real Java exception thrown by user code via {@code throw new ...}.
     *
     * @param type   the simple class name, e.g. {@code IllegalArgumentException}
     * @param detail the part after the colon, or null for none
     */
    public static InterpreterException thrown(String type, String detail, int line) {
        return new InterpreterException(
                detail == null || detail.isBlank() ? type : type + ": " + detail, line);
    }

    public int line() {
        return line;
    }

    /** The Java exception class name when catchable, otherwise null. */
    public String exceptionType() {
        return exceptionType;
    }

    public boolean isCatchable() {
        return exceptionType != null;
    }

    /** The detail after the exception name, for use as the caught exception's message. */
    public String detail() {
        String body = stripLinePrefix(getMessage());
        if (exceptionType == null) {
            return body;
        }
        int colon = body.indexOf(':');
        return colon < 0 ? "" : body.substring(colon + 1).trim();
    }

    private static String deriveType(String message) {
        if (message == null) {
            return null;
        }
        String body = stripLinePrefix(message);
        int colon = body.indexOf(':');
        if (colon > 0) {
            String candidate = body.substring(0, colon).trim();
            if (CATCHABLE.contains(candidate)) {
                return candidate;
            }
        }
        // The two Java messages that carry no exception name of their own.
        if (body.startsWith("/ by zero")) {
            return "ArithmeticException";
        }
        if (body.startsWith("Negative array size")) {
            return "NegativeArraySizeException";
        }
        return null;
    }

    private static String stripLinePrefix(String message) {
        if (message == null || !message.startsWith("Line ")) {
            return message == null ? "" : message;
        }
        int colon = message.indexOf(": ");
        return colon < 0 ? message : message.substring(colon + 2);
    }
}
