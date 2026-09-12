package com.algolens.execution.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hand-written scanner for the supported Java subset.
 *
 * <p>Deliberately small: no generics, annotations, lambdas or unicode escapes. Anything it does
 * not recognise becomes a {@link SyntaxException} with a line number, which the API surfaces as
 * a compile error pointing at the offending row in the editor.
 */
public final class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("class", TokenType.CLASS),
            Map.entry("interface", TokenType.INTERFACE),
            Map.entry("enum", TokenType.ENUM),
            Map.entry("public", TokenType.PUBLIC),
            Map.entry("private", TokenType.PRIVATE),
            Map.entry("protected", TokenType.PROTECTED),
            Map.entry("static", TokenType.STATIC),
            Map.entry("final", TokenType.FINAL),
            Map.entry("void", TokenType.VOID),
            Map.entry("if", TokenType.IF),
            Map.entry("else", TokenType.ELSE),
            Map.entry("for", TokenType.FOR),
            Map.entry("while", TokenType.WHILE),
            Map.entry("do", TokenType.DO),
            Map.entry("return", TokenType.RETURN),
            Map.entry("break", TokenType.BREAK),
            Map.entry("continue", TokenType.CONTINUE),
            Map.entry("new", TokenType.NEW),
            Map.entry("this", TokenType.THIS),
            Map.entry("true", TokenType.TRUE),
            Map.entry("false", TokenType.FALSE),
            Map.entry("null", TokenType.NULL),
            Map.entry("import", TokenType.IMPORT),
            Map.entry("package", TokenType.PACKAGE),
            Map.entry("switch", TokenType.SWITCH),
            Map.entry("case", TokenType.CASE),
            Map.entry("default", TokenType.DEFAULT),
            Map.entry("try", TokenType.TRY),
            Map.entry("catch", TokenType.CATCH),
            Map.entry("finally", TokenType.FINALLY),
            Map.entry("throw", TokenType.THROW),
            Map.entry("throws", TokenType.THROWS),
            Map.entry("instanceof", TokenType.INSTANCEOF),
            Map.entry("int", TokenType.T_INT),
            Map.entry("long", TokenType.T_LONG),
            Map.entry("double", TokenType.T_DOUBLE),
            Map.entry("float", TokenType.T_FLOAT),
            Map.entry("short", TokenType.T_SHORT),
            Map.entry("byte", TokenType.T_BYTE),
            Map.entry("boolean", TokenType.T_BOOLEAN),
            Map.entry("char", TokenType.T_CHAR),
            Map.entry("String", TokenType.T_STRING));

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start;
    private int current;
    private int line = 1;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> scan() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case ' ', '\r', '\t', '\f' -> {
                // insignificant
            }
            case '\n' -> line++;
            case '(' -> add(TokenType.LPAREN);
            case ')' -> add(TokenType.RPAREN);
            case '{' -> add(TokenType.LBRACE);
            case '}' -> add(TokenType.RBRACE);
            case '[' -> add(TokenType.LBRACKET);
            case ']' -> add(TokenType.RBRACKET);
            case ';' -> add(TokenType.SEMICOLON);
            case ',' -> add(TokenType.COMMA);
            case ':' -> add(TokenType.COLON);
            case '?' -> add(TokenType.QUESTION);
            case '~' -> add(TokenType.TILDE);
            case '.' -> {
                if (isDigit(peek())) {
                    number();
                } else {
                    add(TokenType.DOT);
                }
            }
            case '+' -> add(match('+') ? TokenType.INCREMENT
                    : match('=') ? TokenType.PLUS_ASSIGN : TokenType.PLUS);
            case '-' -> add(match('-') ? TokenType.DECREMENT
                    : match('=') ? TokenType.MINUS_ASSIGN
                            : match('>') ? TokenType.ARROW : TokenType.MINUS);
            case '*' -> add(match('=') ? TokenType.STAR_ASSIGN : TokenType.STAR);
            case '%' -> add(match('=') ? TokenType.PERCENT_ASSIGN : TokenType.PERCENT);
            case '^' -> add(match('=') ? TokenType.CARET_ASSIGN : TokenType.CARET);
            case '!' -> add(match('=') ? TokenType.NE : TokenType.NOT);
            case '=' -> add(match('=') ? TokenType.EQ : TokenType.ASSIGN);
            case '&' -> add(match('&') ? TokenType.AND_AND
                    : match('=') ? TokenType.AMP_ASSIGN : TokenType.AMP);
            case '|' -> add(match('|') ? TokenType.OR_OR
                    : match('=') ? TokenType.PIPE_ASSIGN : TokenType.PIPE);
            case '<' -> {
                if (match('<')) {
                    add(match('=') ? TokenType.SHL_ASSIGN : TokenType.SHL);
                } else {
                    add(match('=') ? TokenType.LE : TokenType.LT);
                }
            }
            case '>' -> {
                if (match('>')) {
                    if (match('>')) {
                        add(TokenType.USHR);
                    } else {
                        add(match('=') ? TokenType.SHR_ASSIGN : TokenType.SHR);
                    }
                } else {
                    add(match('=') ? TokenType.GE : TokenType.GT);
                }
            }
            case '/' -> {
                if (match('/')) {
                    while (!isAtEnd() && peek() != '\n') {
                        advance();
                    }
                } else if (match('*')) {
                    blockComment();
                } else {
                    add(match('=') ? TokenType.SLASH_ASSIGN : TokenType.SLASH);
                }
            }
            case '"' -> string();
            case '\'' -> charLiteral();
            default -> {
                if (isDigit(c)) {
                    number();
                } else if (isIdentifierStart(c)) {
                    identifier();
                } else {
                    throw new SyntaxException("Unexpected character '" + c + "'", line);
                }
            }
        }
    }

    private void blockComment() {
        while (!isAtEnd()) {
            char c = advance();
            if (c == '\n') {
                line++;
            } else if (c == '*' && peek() == '/') {
                advance();
                return;
            }
        }
        throw new SyntaxException("Unterminated block comment", line);
    }

    private void string() {
        StringBuilder value = new StringBuilder();
        while (!isAtEnd() && peek() != '"') {
            char c = advance();
            if (c == '\n') {
                throw new SyntaxException("Unterminated string literal", line);
            }
            value.append(c == '\\' ? escape() : c);
        }
        if (isAtEnd()) {
            throw new SyntaxException("Unterminated string literal", line);
        }
        advance(); // closing quote
        tokens.add(new Token(TokenType.STRING_LITERAL, source.substring(start, current),
                value.toString(), line));
    }

    private void charLiteral() {
        if (isAtEnd()) {
            throw new SyntaxException("Unterminated character literal", line);
        }
        char c = advance();
        char value = c == '\\' ? escape() : c;
        if (isAtEnd() || advance() != '\'') {
            throw new SyntaxException("Unterminated character literal", line);
        }
        tokens.add(new Token(TokenType.CHAR_LITERAL, source.substring(start, current), value, line));
    }

    private char escape() {
        if (isAtEnd()) {
            throw new SyntaxException("Dangling escape sequence", line);
        }
        char c = advance();
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case '0' -> '\0';
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"' -> '"';
            default -> throw new SyntaxException("Unsupported escape sequence '\\" + c + "'", line);
        };
    }

    private void number() {
        while (isDigit(peek()) || peek() == '_') {
            advance();
        }
        // A literal that started with '.' (as in ".5") is a double by construction.
        boolean isDouble = source.charAt(start) == '.';
        if (peek() == '.' && isDigit(peekNext())) {
            isDouble = true;
            advance();
            while (isDigit(peek()) || peek() == '_') {
                advance();
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            int save = current;
            advance();
            if (peek() == '+' || peek() == '-') {
                advance();
            }
            if (isDigit(peek())) {
                isDouble = true;
                while (isDigit(peek())) {
                    advance();
                }
            } else {
                current = save;
            }
        }

        String text = source.substring(start, current).replace("_", "");
        char suffix = peek();
        if (suffix == 'L' || suffix == 'l') {
            advance();
            tokens.add(new Token(TokenType.LONG_LITERAL, text, Long.parseLong(text), line));
            return;
        }
        if (suffix == 'd' || suffix == 'D' || suffix == 'f' || suffix == 'F') {
            advance();
            tokens.add(new Token(TokenType.DOUBLE_LITERAL, text, Double.parseDouble(text), line));
            return;
        }
        if (isDouble) {
            tokens.add(new Token(TokenType.DOUBLE_LITERAL, text, Double.parseDouble(text), line));
            return;
        }
        try {
            tokens.add(new Token(TokenType.INT_LITERAL, text, Integer.parseInt(text), line));
        } catch (NumberFormatException e) {
            // Matches javac's behaviour closely enough: too big for int, keep it as a long.
            tokens.add(new Token(TokenType.LONG_LITERAL, text, Long.parseLong(text), line));
        }
    }

    private void identifier() {
        while (isIdentifierPart(peek())) {
            advance();
        }
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.get(text);
        if (type == null) {
            add(TokenType.IDENTIFIER);
            return;
        }
        switch (type) {
            case TRUE -> tokens.add(new Token(type, text, Boolean.TRUE, line));
            case FALSE -> tokens.add(new Token(type, text, Boolean.FALSE, line));
            default -> add(type);
        }
    }

    private void add(TokenType type) {
        tokens.add(new Token(type, source.substring(start, current), null, line));
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) {
            return false;
        }
        current++;
        return true;
    }

    private char advance() {
        return source.charAt(current++);
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext() {
        return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }
}
