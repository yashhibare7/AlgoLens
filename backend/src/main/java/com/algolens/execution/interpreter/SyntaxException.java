package com.algolens.execution.interpreter;

/** Raised by the lexer or parser. Maps to {@code COMPILE_ERROR}. */
public class SyntaxException extends RuntimeException {

    private final int line;

    public SyntaxException(String message, int line) {
        super(line > 0 ? "Line " + line + ": " + message : message);
        this.line = line;
    }

    public int line() {
        return line;
    }
}
