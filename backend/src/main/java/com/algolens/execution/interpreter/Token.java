package com.algolens.execution.interpreter;

/**
 * @param type    what kind of token this is
 * @param lexeme  the exact source text
 * @param literal the decoded value for literal tokens, otherwise null
 * @param line    1-based source line, carried all the way to the trace so the editor can
 *                highlight the right row
 */
public record Token(TokenType type, String lexeme, Object literal, int line) {

    @Override
    public String toString() {
        return type + "('" + lexeme + "')@" + line;
    }
}
