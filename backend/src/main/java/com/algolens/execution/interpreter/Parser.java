package com.algolens.execution.interpreter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recursive-descent parser for the supported Java subset.
 *
 * <p>Accepts the shapes people actually paste, in any combination:
 *
 * <ol>
 *   <li>a full class with a {@code main} method, optionally with nested {@code static class}
 *       helpers;</li>
 *   <li>several top-level classes, one of which has {@code main};</li>
 *   <li>bare statements with no class or method wrapper;</li>
 *   <li>bare statements plus helper methods and/or class declarations.</li>
 * </ol>
 *
 * <p>Java's grammar is ambiguous between a local declaration and an expression statement until
 * you have looked ahead a few tokens ({@code Foo bar;} vs {@code foo * bar;}).
 * {@link #looksLikeLocalDeclaration()} settles it with bounded lookahead: a type is a primitive
 * keyword or an identifier, so {@code IDENT IDENT} is a declaration and {@code IDENT *} is not.
 */
public final class Parser {

    private static final Set<TokenType> TYPE_KEYWORDS = EnumSet.of(
            TokenType.T_INT, TokenType.T_LONG, TokenType.T_DOUBLE, TokenType.T_FLOAT,
            TokenType.T_SHORT, TokenType.T_BYTE, TokenType.T_BOOLEAN, TokenType.T_CHAR,
            TokenType.T_STRING);

    private static final Set<TokenType> MODIFIERS = EnumSet.of(
            TokenType.PUBLIC, TokenType.PRIVATE, TokenType.PROTECTED, TokenType.STATIC,
            TokenType.FINAL);

    private final List<Token> tokens;
    private int current;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Ast.Program parse(String source) {
        return new Parser(new Lexer(source).scan()).parseProgram();
    }

    // ------------------------------------------------------------------ program

    /**
     * Parses the whole submission with one loop that accepts classes, methods, static fields and
     * statements in any order. That single loop is what makes all four accepted shapes work
     * without the caller having to declare which one they are using.
     */
    public Ast.Program parseProgram() {
        skipPackageAndImports();

        List<Ast.ClassDecl> topLevelClasses = new ArrayList<>();
        List<Ast.MethodDecl> topLevelMethods = new ArrayList<>();
        List<Ast.VarDecl> topLevelFields = new ArrayList<>();
        List<Ast.Stmt> statements = new ArrayList<>();

        while (!check(TokenType.EOF)) {
            if (match(TokenType.SEMICOLON)) {
                continue;
            }
            if (isClassDeclarationAhead()) {
                topLevelClasses.add(classDeclaration());
            } else if (looksLikeMethodDeclaration()) {
                topLevelMethods.add(methodDeclaration(null));
            } else if (isStaticFieldAhead()) {
                topLevelFields.add(staticFieldDeclaration());
            } else {
                statements.add(statement());
            }
        }

        List<Ast.ClassDecl> classes = flatten(topLevelClasses);

        // Bare statements are their own entry point; methods and classes around them are
        // support, not the thing to run.
        if (!statements.isEmpty()) {
            List<Ast.MethodDecl> callable = new ArrayList<>(topLevelMethods);
            return new Ast.Program("Snippet", classes, topLevelFields, callable, statements, null);
        }

        return withEntryPoint(classes, topLevelMethods, topLevelFields);
    }

    /**
     * Picks what to run: a {@code main} method if one exists anywhere, otherwise the only
     * method if there is exactly one and it takes no arguments.
     */
    private Ast.Program withEntryPoint(List<Ast.ClassDecl> classes,
            List<Ast.MethodDecl> topLevelMethods, List<Ast.VarDecl> topLevelFields) {

        for (Ast.ClassDecl declaration : classes) {
            for (Ast.MethodDecl method : declaration.methods()) {
                if ("main".equals(method.name())) {
                    List<Ast.MethodDecl> callable = new ArrayList<>(declaration.methods());
                    callable.addAll(topLevelMethods);
                    List<Ast.VarDecl> fields = new ArrayList<>(declaration.staticFields());
                    fields.addAll(topLevelFields);
                    return new Ast.Program(declaration.name(), classes, fields, callable,
                            List.of(), "main");
                }
            }
        }

        if (topLevelMethods.size() == 1 && topLevelMethods.get(0).parameters().isEmpty()) {
            return new Ast.Program("Snippet", classes, topLevelFields, topLevelMethods, List.of(),
                    topLevelMethods.get(0).name());
        }

        for (Ast.ClassDecl declaration : classes) {
            List<Ast.MethodDecl> candidates = declaration.methods();
            if (candidates.size() == 1 && candidates.get(0).parameters().isEmpty()) {
                List<Ast.MethodDecl> callable = new ArrayList<>(candidates);
                callable.addAll(topLevelMethods);
                return new Ast.Program(declaration.name(), classes,
                        declaration.staticFields(), callable, List.of(),
                        candidates.get(0).name());
            }
        }

        // A LeetCode-style `class Solution` with no main is the most common submission there
        // is, so the message hands back a runnable main rather than just refusing.
        throw new SyntaxException(EntryPointHelp.message(classes, topLevelMethods), 0);
    }

    /**
     * Flattens nested classes by simple name. The subset has no inner-class scoping, and
     * {@code static class Node} inside {@code Main} is how most single-file linked lists are
     * written, so a flat namespace is both simpler and enough.
     */
    private List<Ast.ClassDecl> flatten(List<Ast.ClassDecl> roots) {
        List<Ast.ClassDecl> flat = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collect(roots, flat, seen);
        return flat;
    }

    private void collect(List<Ast.ClassDecl> declarations, List<Ast.ClassDecl> into,
            Set<String> seen) {
        for (Ast.ClassDecl declaration : declarations) {
            if (!seen.add(declaration.name())) {
                throw new SyntaxException(
                        "Class '" + declaration.name() + "' is declared more than once",
                        declaration.line());
            }
            into.add(declaration);
            collect(declaration.nested(), into, seen);
        }
    }

    // ------------------------------------------------------------------ class declarations

    private Ast.ClassDecl classDeclaration() {
        int line = peek().line();
        while (MODIFIERS.contains(peek().type())) {
            advance();
        }
        if (check(TokenType.INTERFACE) || check(TokenType.ENUM)) {
            throw error(peek(), "Only plain classes are supported (no interfaces or enums yet)");
        }
        consume(TokenType.CLASS, "Expected 'class'");
        Token name = consume(TokenType.IDENTIFIER, "Expected a class name");
        if (check(TokenType.IDENTIFIER)) {
            throw error(peek(), "'extends' and 'implements' are not supported yet");
        }
        consume(TokenType.LBRACE, "Expected '{' after the class name");

        List<Ast.VarDecl> instanceFields = new ArrayList<>();
        List<Ast.VarDecl> staticFields = new ArrayList<>();
        List<Ast.MethodDecl> methods = new ArrayList<>();
        List<Ast.MethodDecl> constructors = new ArrayList<>();
        List<Ast.ClassDecl> nested = new ArrayList<>();

        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            if (match(TokenType.SEMICOLON)) {
                continue;
            }
            if (isClassDeclarationAhead()) {
                nested.add(classDeclaration());
                continue;
            }
            if (isConstructorAhead(name.lexeme())) {
                constructors.add(constructorDeclaration(name.lexeme()));
                continue;
            }
            if (looksLikeMethodDeclaration()) {
                methods.add(methodDeclaration(name.lexeme()));
                continue;
            }
            boolean isStatic = hasStaticModifier();
            Ast.VarDecl field = fieldDeclaration();
            (isStatic ? staticFields : instanceFields).add(field);
        }

        consume(TokenType.RBRACE, "Expected '}' to close the class body");
        return new Ast.ClassDecl(line, name.lexeme(), instanceFields, staticFields, methods,
                constructors, nested);
    }

    private Ast.MethodDecl constructorDeclaration(String className) {
        int line = peek().line();
        while (MODIFIERS.contains(peek().type())) {
            advance();
        }
        consume(TokenType.IDENTIFIER, "Expected the constructor name");
        List<Ast.Param> parameters = parameterList();
        Ast.Block body = block();
        // returnType null marks a constructor; a constructor is never static.
        return new Ast.MethodDecl(line, null, className, parameters, body, false);
    }

    private Ast.MethodDecl methodDeclaration(String enclosingClass) {
        int line = peek().line();
        boolean isStatic = false;
        while (MODIFIERS.contains(peek().type())) {
            isStatic |= check(TokenType.STATIC);
            advance();
        }
        String returnType = match(TokenType.VOID) ? "void" : parseType();
        Token name = consume(TokenType.IDENTIFIER, "Expected a method name");
        List<Ast.Param> parameters = parameterList();
        Ast.Block body = block();

        // A method declared outside any class behaves as static: there is no instance to bind.
        boolean effectivelyStatic = isStatic || enclosingClass == null;
        return new Ast.MethodDecl(line, returnType, name.lexeme(), parameters, body,
                effectivelyStatic);
    }

    private List<Ast.Param> parameterList() {
        consume(TokenType.LPAREN, "Expected '(' after the method name");
        List<Ast.Param> parameters = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                match(TokenType.FINAL);
                String type = parseType();
                Token name = consume(TokenType.IDENTIFIER, "Expected a parameter name");
                parameters.add(new Ast.Param(type + trailingDimensions(), name.lexeme()));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after the parameters");
        // A 'throws' clause is accepted and ignored: the subset has no checked exceptions, so
        // it carries no meaning here, but rejecting it would fail perfectly ordinary code.
        if (match(TokenType.THROWS)) {
            do {
                parseType();
            } while (match(TokenType.COMMA));
        }
        return parameters;
    }

    private Ast.VarDecl fieldDeclaration() {
        while (MODIFIERS.contains(peek().type())) {
            advance();
        }
        return declaration();
    }

    private Ast.VarDecl staticFieldDeclaration() {
        while (MODIFIERS.contains(peek().type())) {
            advance();
        }
        return declaration();
    }

    private void skipPackageAndImports() {
        while (check(TokenType.PACKAGE) || check(TokenType.IMPORT)) {
            while (!check(TokenType.SEMICOLON) && !check(TokenType.EOF)) {
                advance();
            }
            match(TokenType.SEMICOLON);
        }
    }

    // ------------------------------------------------------------------ lookahead helpers

    private boolean isClassDeclarationAhead() {
        int save = current;
        try {
            while (MODIFIERS.contains(peek().type())) {
                advance();
            }
            return check(TokenType.CLASS) || check(TokenType.INTERFACE) || check(TokenType.ENUM);
        } finally {
            current = save;
        }
    }

    /** {@code [modifiers] ClassName (} -- a constructor, which has no return type. */
    private boolean isConstructorAhead(String className) {
        int save = current;
        try {
            while (MODIFIERS.contains(peek().type())) {
                advance();
            }
            return check(TokenType.IDENTIFIER)
                    && peek().lexeme().equals(className)
                    && checkNext(TokenType.LPAREN);
        } finally {
            current = save;
        }
    }

    /** True when the tokens ahead read as {@code [modifiers] type name (}. */
    private boolean looksLikeMethodDeclaration() {
        int save = current;
        try {
            while (MODIFIERS.contains(peek().type())) {
                advance();
            }
            if (match(TokenType.VOID)) {
                return check(TokenType.IDENTIFIER) && checkNext(TokenType.LPAREN);
            }
            if (!skipTypeTokens()) {
                return false;
            }
            return check(TokenType.IDENTIFIER) && checkNext(TokenType.LPAREN);
        } finally {
            current = save;
        }
    }

    /** A top-level {@code static int count = 0;} belongs to the program, not to a statement. */
    private boolean isStaticFieldAhead() {
        int save = current;
        try {
            boolean sawModifier = false;
            while (MODIFIERS.contains(peek().type())) {
                sawModifier |= !check(TokenType.FINAL);
                advance();
            }
            return sawModifier && skipTypeTokens() && check(TokenType.IDENTIFIER);
        } finally {
            current = save;
        }
    }

    private boolean hasStaticModifier() {
        int save = current;
        try {
            while (MODIFIERS.contains(peek().type())) {
                if (check(TokenType.STATIC)) {
                    return true;
                }
                advance();
            }
            return false;
        } finally {
            current = save;
        }
    }

    /** True when the tokens ahead read as {@code type name} (a local variable declaration). */
    private boolean looksLikeLocalDeclaration() {
        if (check(TokenType.FINAL) || TYPE_KEYWORDS.contains(peek().type())) {
            return true;
        }
        if (!check(TokenType.IDENTIFIER)) {
            return false;
        }
        int save = current;
        try {
            if (!skipTypeTokens()) {
                return false;
            }
            return check(TokenType.IDENTIFIER);
        } finally {
            current = save;
        }
    }

    /** Consumes a type if one is present, returning false when the tokens are not a type. */
    private boolean skipTypeTokens() {
        if (TYPE_KEYWORDS.contains(peek().type()) || check(TokenType.IDENTIFIER)) {
            advance();
        } else {
            return false;
        }
        // Dotted names: java.util.List, Map.Entry.
        while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
            advance();
            advance();
        }
        consumeGenericArguments();
        while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
            advance();
            advance();
        }
        return true;
    }

    private String parseType() {
        Token token = peek();
        if (!TYPE_KEYWORDS.contains(token.type()) && !check(TokenType.IDENTIFIER)) {
            throw error(token, "Expected a type name");
        }
        StringBuilder text = new StringBuilder(advance().lexeme());
        while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
            advance();
            text.append('.').append(advance().lexeme());
        }
        text.append(consumeGenericArguments());
        text.append(trailingDimensions());
        return text.toString();
    }

    /**
     * Consumes a balanced {@code <...>} and returns its source text, or {@code ""} when the
     * tokens ahead are not type arguments.
     *
     * <p>Two things make this fiddly. First, {@code Map<String, List<Integer>>} ends in a single
     * {@code >>} token, so closing a level has to account for {@code >>} and {@code >>>} closing
     * two and three. Second, {@code a < b} is a comparison, not the start of a type -- so
     * anything that does not look like type arguments restores the cursor and reports "no
     * generics here", letting the caller fall back to parsing an expression.
     */
    private String consumeGenericArguments() {
        if (!check(TokenType.LT)) {
            return "";
        }
        int save = current;
        StringBuilder text = new StringBuilder();
        int depth = 0;

        while (true) {
            Token token = peek();
            switch (token.type()) {
                case LT -> {
                    depth++;
                    advance();
                    text.append('<');
                }
                case GT -> {
                    depth--;
                    advance();
                    text.append('>');
                }
                case SHR -> {
                    depth -= 2;
                    advance();
                    text.append(">>");
                }
                case USHR -> {
                    depth -= 3;
                    advance();
                    text.append(">>>");
                }
                case IDENTIFIER, COMMA, DOT, LBRACKET, RBRACKET, QUESTION, T_INT, T_LONG,
                        T_DOUBLE, T_FLOAT, T_SHORT, T_BYTE, T_BOOLEAN, T_CHAR, T_STRING -> {
                    advance();
                    text.append(token.lexeme());
                }
                default -> {
                    current = save;
                    return "";
                }
            }
            if (depth == 0) {
                return text.toString();
            }
            if (depth < 0) {
                current = save;
                return "";
            }
        }
    }

    private String trailingDimensions() {
        StringBuilder dimensions = new StringBuilder();
        while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
            advance();
            advance();
            dimensions.append("[]");
        }
        return dimensions.toString();
    }

    // ------------------------------------------------------------------ statements

    private Ast.Stmt statement() {
        if (check(TokenType.LBRACE)) {
            return block();
        }
        if (check(TokenType.IF)) {
            return ifStatement();
        }
        if (check(TokenType.WHILE)) {
            return whileStatement();
        }
        if (check(TokenType.DO)) {
            return doWhileStatement();
        }
        if (check(TokenType.FOR)) {
            return forStatement();
        }
        if (check(TokenType.RETURN)) {
            return returnStatement();
        }
        if (check(TokenType.SWITCH)) {
            return switchStatement();
        }
        if (check(TokenType.TRY)) {
            return tryStatement();
        }
        if (check(TokenType.THROW)) {
            return throwStatement();
        }
        if (check(TokenType.BREAK)) {
            int line = advance().line();
            consume(TokenType.SEMICOLON, "Expected ';' after 'break'");
            return new Ast.Break(line);
        }
        if (check(TokenType.CONTINUE)) {
            int line = advance().line();
            consume(TokenType.SEMICOLON, "Expected ';' after 'continue'");
            return new Ast.Continue(line);
        }
        if (check(TokenType.SEMICOLON)) {
            return new Ast.Empty(advance().line());
        }
        // 'final' is legal on a local declaration; the other modifiers are not.
        if (check(TokenType.CLASS)
                || (MODIFIERS.contains(peek().type()) && !check(TokenType.FINAL))) {
            throw error(peek(), "Declarations are not allowed here");
        }
        if (looksLikeLocalDeclaration()) {
            return declaration();
        }
        int line = peek().line();
        Ast.Expr expression = expression();
        consume(TokenType.SEMICOLON, "Expected ';' after the expression");
        return new Ast.ExprStmt(line, expression);
    }

    private Ast.Block block() {
        int line = consume(TokenType.LBRACE, "Expected '{'").line();
        List<Ast.Stmt> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            statements.add(statement());
        }
        consume(TokenType.RBRACE, "Expected '}'");
        return new Ast.Block(line, statements);
    }

    private Ast.VarDecl declaration() {
        int line = peek().line();
        match(TokenType.FINAL);
        String type = parseType();
        List<Ast.Declarator> declarators = new ArrayList<>();
        do {
            Token name = consume(TokenType.IDENTIFIER, "Expected a variable name");
            int extraDimensions = trailingDimensions().length() / 2;
            Ast.Expr initializer = null;
            if (match(TokenType.ASSIGN)) {
                initializer = variableInitializer(type + "[]".repeat(extraDimensions));
            }
            declarators.add(new Ast.Declarator(name.lexeme(), extraDimensions, initializer));
        } while (match(TokenType.COMMA));
        consume(TokenType.SEMICOLON, "Expected ';' after the declaration");
        return new Ast.VarDecl(line, type, declarators);
    }

    /** Handles the {@code {1, 2, 3}} form, which is only legal as an initialiser. */
    private Ast.Expr variableInitializer(String declaredType) {
        if (check(TokenType.LBRACE)) {
            return arrayLiteral(elementTypeOf(declaredType));
        }
        return expression();
    }

    private Ast.Expr arrayLiteral(String elementType) {
        int line = consume(TokenType.LBRACE, "Expected '{'").line();
        List<Ast.Expr> elements = new ArrayList<>();
        if (!check(TokenType.RBRACE)) {
            do {
                if (check(TokenType.RBRACE)) {
                    break; // tolerate a trailing comma
                }
                if (check(TokenType.LBRACE)) {
                    elements.add(arrayLiteral(elementTypeOf(elementType)));
                } else {
                    elements.add(expression());
                }
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RBRACE, "Expected '}' to close the array initialiser");
        return new Ast.ArrayLiteral(line, elementType, elements);
    }

    private static String elementTypeOf(String arrayType) {
        return arrayType != null && arrayType.endsWith("[]")
                ? arrayType.substring(0, arrayType.length() - 2)
                : arrayType;
    }

    private Ast.Stmt ifStatement() {
        int line = consume(TokenType.IF, "Expected 'if'").line();
        consume(TokenType.LPAREN, "Expected '(' after 'if'");
        Ast.Expr condition = expression();
        consume(TokenType.RPAREN, "Expected ')' after the condition");
        Ast.Stmt thenBranch = statement();
        Ast.Stmt elseBranch = match(TokenType.ELSE) ? statement() : null;
        return new Ast.If(line, condition, thenBranch, elseBranch);
    }

    private Ast.Stmt whileStatement() {
        int line = consume(TokenType.WHILE, "Expected 'while'").line();
        consume(TokenType.LPAREN, "Expected '(' after 'while'");
        Ast.Expr condition = expression();
        consume(TokenType.RPAREN, "Expected ')' after the condition");
        return new Ast.While(line, condition, statement());
    }

    private Ast.Stmt doWhileStatement() {
        int line = consume(TokenType.DO, "Expected 'do'").line();
        Ast.Stmt body = statement();
        consume(TokenType.WHILE, "Expected 'while' after the 'do' body");
        consume(TokenType.LPAREN, "Expected '(' after 'while'");
        Ast.Expr condition = expression();
        consume(TokenType.RPAREN, "Expected ')' after the condition");
        consume(TokenType.SEMICOLON, "Expected ';' after 'do ... while (...)'");
        return new Ast.DoWhile(line, body, condition);
    }

    private Ast.Stmt forStatement() {
        int line = consume(TokenType.FOR, "Expected 'for'").line();
        consume(TokenType.LPAREN, "Expected '(' after 'for'");

        if (isForEachAhead()) {
            match(TokenType.FINAL);
            String type = parseType();
            Token name = consume(TokenType.IDENTIFIER, "Expected the loop variable name");
            consume(TokenType.COLON, "Expected ':' in the for-each loop");
            Ast.Expr iterable = expression();
            consume(TokenType.RPAREN, "Expected ')' after the for-each header");
            return new Ast.ForEach(line, type, name.lexeme(), iterable, statement());
        }

        List<Ast.Stmt> initializers = new ArrayList<>();
        if (!check(TokenType.SEMICOLON)) {
            if (looksLikeLocalDeclaration()) {
                initializers.add(declaration()); // consumes the ';'
            } else {
                do {
                    int exprLine = peek().line();
                    initializers.add(new Ast.ExprStmt(exprLine, expression()));
                } while (match(TokenType.COMMA));
                consume(TokenType.SEMICOLON, "Expected ';' after the loop initialiser");
            }
        } else {
            advance();
        }

        Ast.Expr condition = check(TokenType.SEMICOLON) ? null : expression();
        consume(TokenType.SEMICOLON, "Expected ';' after the loop condition");

        List<Ast.Expr> updates = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                updates.add(expression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after the loop header");
        return new Ast.For(line, initializers, condition, updates, statement());
    }

    private boolean isForEachAhead() {
        int save = current;
        try {
            match(TokenType.FINAL);
            if (!skipTypeTokens()) {
                return false;
            }
            if (!check(TokenType.IDENTIFIER)) {
                return false;
            }
            advance();
            return check(TokenType.COLON);
        } finally {
            current = save;
        }
    }

    /**
     * Both switch forms: the classic {@code case 1:} with fall-through, and {@code case 1 ->}
     * which never falls through. Stacked labels ({@code case 1: case 2: ...}) parse as a case
     * with an empty body, and fall-through then does the right thing at runtime.
     */
    private Ast.Stmt switchStatement() {
        int line = consume(TokenType.SWITCH, "Expected 'switch'").line();
        consume(TokenType.LPAREN, "Expected '(' after 'switch'");
        Ast.Expr selector = expression();
        consume(TokenType.RPAREN, "Expected ')' after the switch value");
        consume(TokenType.LBRACE, "Expected '{' to open the switch body");

        List<Ast.SwitchCase> cases = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            int caseLine = peek().line();
            List<Ast.Expr> labels = new ArrayList<>();

            if (!match(TokenType.DEFAULT)) {
                consume(TokenType.CASE, "Expected 'case' or 'default'");
                do {
                    // ternary, not assignment: a label cannot contain '='.
                    labels.add(ternary());
                } while (match(TokenType.COMMA));
            }

            boolean arrowForm = match(TokenType.ARROW);
            if (!arrowForm) {
                consume(TokenType.COLON, "Expected ':' or '->' after the case label");
            }

            List<Ast.Stmt> body = new ArrayList<>();
            if (arrowForm) {
                body.add(check(TokenType.LBRACE) ? block() : statement());
            } else {
                while (!check(TokenType.CASE) && !check(TokenType.DEFAULT)
                        && !check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                    body.add(statement());
                }
            }
            cases.add(new Ast.SwitchCase(caseLine, labels, body, arrowForm));
        }

        consume(TokenType.RBRACE, "Expected '}' to close the switch body");
        return new Ast.Switch(line, selector, cases);
    }

    private Ast.Stmt tryStatement() {
        int line = consume(TokenType.TRY, "Expected 'try'").line();
        if (check(TokenType.LPAREN)) {
            throw error(peek(), "try-with-resources is not supported yet");
        }
        Ast.Block body = block();

        List<Ast.CatchClause> catches = new ArrayList<>();
        while (check(TokenType.CATCH)) {
            int catchLine = advance().line();
            consume(TokenType.LPAREN, "Expected '(' after 'catch'");
            match(TokenType.FINAL);
            List<String> types = new ArrayList<>();
            types.add(parseType());
            // Multi-catch: catch (A | B e)
            while (match(TokenType.PIPE)) {
                types.add(parseType());
            }
            Token name = consume(TokenType.IDENTIFIER, "Expected the exception variable name");
            consume(TokenType.RPAREN, "Expected ')' after the catch parameter");
            catches.add(new Ast.CatchClause(catchLine, types, name.lexeme(), block()));
        }

        Ast.Block finallyBlock = match(TokenType.FINALLY) ? block() : null;
        if (catches.isEmpty() && finallyBlock == null) {
            throw error(peek(), "A 'try' needs at least one 'catch' or a 'finally'");
        }
        return new Ast.Try(line, body, catches, finallyBlock);
    }

    private Ast.Stmt throwStatement() {
        int line = consume(TokenType.THROW, "Expected 'throw'").line();
        Ast.Expr value = expression();
        consume(TokenType.SEMICOLON, "Expected ';' after 'throw'");
        return new Ast.Throw(line, value);
    }

    private Ast.Stmt returnStatement() {
        int line = consume(TokenType.RETURN, "Expected 'return'").line();
        Ast.Expr value = check(TokenType.SEMICOLON) ? null : expression();
        consume(TokenType.SEMICOLON, "Expected ';' after 'return'");
        return new Ast.Return(line, value);
    }

    // ------------------------------------------------------------------ expressions

    private Ast.Expr expression() {
        return assignment();
    }

    private Ast.Expr assignment() {
        Ast.Expr left = ternary();
        TokenType type = peek().type();
        String operator = switch (type) {
            case ASSIGN -> "=";
            case PLUS_ASSIGN -> "+=";
            case MINUS_ASSIGN -> "-=";
            case STAR_ASSIGN -> "*=";
            case SLASH_ASSIGN -> "/=";
            case PERCENT_ASSIGN -> "%=";
            case AMP_ASSIGN -> "&=";
            case PIPE_ASSIGN -> "|=";
            case CARET_ASSIGN -> "^=";
            case SHL_ASSIGN -> "<<=";
            case SHR_ASSIGN -> ">>=";
            default -> null;
        };
        if (operator == null) {
            return left;
        }
        Token token = advance();
        if (!isAssignable(left)) {
            throw error(token, "Cannot assign to this expression");
        }
        Ast.Expr value = assignment();
        return new Ast.Assign(token.line(), left, operator, value);
    }

    /** A variable, an array element, or an object field. */
    private static boolean isAssignable(Ast.Expr target) {
        return target instanceof Ast.Name || target instanceof Ast.Index
                || target instanceof Ast.Field;
    }

    private Ast.Expr ternary() {
        Ast.Expr condition = logicalOr();
        if (!check(TokenType.QUESTION)) {
            return condition;
        }
        int line = advance().line();
        Ast.Expr thenBranch = expression();
        consume(TokenType.COLON, "Expected ':' in the conditional expression");
        Ast.Expr elseBranch = assignment();
        return new Ast.Ternary(line, condition, thenBranch, elseBranch);
    }

    private Ast.Expr logicalOr() {
        Ast.Expr expr = logicalAnd();
        while (check(TokenType.OR_OR)) {
            Token token = advance();
            expr = new Ast.Logical(token.line(), expr, "||", logicalAnd());
        }
        return expr;
    }

    private Ast.Expr logicalAnd() {
        Ast.Expr expr = bitwiseOr();
        while (check(TokenType.AND_AND)) {
            Token token = advance();
            expr = new Ast.Logical(token.line(), expr, "&&", bitwiseOr());
        }
        return expr;
    }

    private Ast.Expr bitwiseOr() {
        Ast.Expr expr = bitwiseXor();
        while (check(TokenType.PIPE)) {
            Token token = advance();
            expr = new Ast.Binary(token.line(), expr, "|", bitwiseXor());
        }
        return expr;
    }

    private Ast.Expr bitwiseXor() {
        Ast.Expr expr = bitwiseAnd();
        while (check(TokenType.CARET)) {
            Token token = advance();
            expr = new Ast.Binary(token.line(), expr, "^", bitwiseAnd());
        }
        return expr;
    }

    private Ast.Expr bitwiseAnd() {
        Ast.Expr expr = equality();
        while (check(TokenType.AMP)) {
            Token token = advance();
            expr = new Ast.Binary(token.line(), expr, "&", equality());
        }
        return expr;
    }

    private Ast.Expr equality() {
        Ast.Expr expr = relational();
        while (check(TokenType.EQ) || check(TokenType.NE)) {
            Token token = advance();
            String operator = token.type() == TokenType.EQ ? "==" : "!=";
            expr = new Ast.Binary(token.line(), expr, operator, relational());
        }
        return expr;
    }

    private Ast.Expr relational() {
        Ast.Expr expr = shift();
        while (check(TokenType.LT) || check(TokenType.GT) || check(TokenType.LE)
                || check(TokenType.GE) || check(TokenType.INSTANCEOF)) {
            // instanceof sits at relational precedence in Java, and takes a type on the right
            // rather than an expression.
            if (check(TokenType.INSTANCEOF)) {
                Token token = advance();
                expr = new Ast.InstanceOf(token.line(), expr, parseType());
                continue;
            }
            Token token = advance();
            String operator = switch (token.type()) {
                case LT -> "<";
                case GT -> ">";
                case LE -> "<=";
                default -> ">=";
            };
            expr = new Ast.Binary(token.line(), expr, operator, shift());
        }
        return expr;
    }

    private Ast.Expr shift() {
        Ast.Expr expr = additive();
        while (check(TokenType.SHL) || check(TokenType.SHR) || check(TokenType.USHR)) {
            Token token = advance();
            String operator = switch (token.type()) {
                case SHL -> "<<";
                case SHR -> ">>";
                default -> ">>>";
            };
            expr = new Ast.Binary(token.line(), expr, operator, additive());
        }
        return expr;
    }

    private Ast.Expr additive() {
        Ast.Expr expr = multiplicative();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token token = advance();
            String operator = token.type() == TokenType.PLUS ? "+" : "-";
            expr = new Ast.Binary(token.line(), expr, operator, multiplicative());
        }
        return expr;
    }

    private Ast.Expr multiplicative() {
        Ast.Expr expr = unary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            Token token = advance();
            String operator = switch (token.type()) {
                case STAR -> "*";
                case SLASH -> "/";
                default -> "%";
            };
            expr = new Ast.Binary(token.line(), expr, operator, unary());
        }
        return expr;
    }

    private Ast.Expr unary() {
        if (check(TokenType.NOT) || check(TokenType.MINUS) || check(TokenType.PLUS)
                || check(TokenType.TILDE)) {
            Token token = advance();
            String operator = switch (token.type()) {
                case NOT -> "!";
                case MINUS -> "-";
                case PLUS -> "+";
                default -> "~";
            };
            return new Ast.Unary(token.line(), operator, unary());
        }
        if (check(TokenType.INCREMENT) || check(TokenType.DECREMENT)) {
            Token token = advance();
            String operator = token.type() == TokenType.INCREMENT ? "++" : "--";
            Ast.Expr target = unary();
            if (!isAssignable(target)) {
                throw error(token, "'" + operator + "' needs a variable or field");
            }
            return new Ast.IncDec(token.line(), target, operator, true);
        }
        if (isCastAhead()) {
            Token token = consume(TokenType.LPAREN, "Expected '('");
            String type = parseType();
            consume(TokenType.RPAREN, "Expected ')' after the cast type");
            return new Ast.Cast(token.line(), type, unary());
        }
        return postfix();
    }

    /** Only primitive and {@code String} casts exist in the subset, so this stays unambiguous. */
    private boolean isCastAhead() {
        if (!check(TokenType.LPAREN)) {
            return false;
        }
        int save = current;
        try {
            advance();
            if (!TYPE_KEYWORDS.contains(peek().type())) {
                return false;
            }
            advance();
            while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
                advance();
                advance();
            }
            return check(TokenType.RPAREN);
        } finally {
            current = save;
        }
    }

    private Ast.Expr postfix() {
        Ast.Expr expr = primary();
        while (true) {
            if (check(TokenType.LBRACKET)) {
                Token token = advance();
                Ast.Expr index = expression();
                consume(TokenType.RBRACKET, "Expected ']' after the array index");
                expr = new Ast.Index(token.line(), expr, index);
            } else if (check(TokenType.DOT)) {
                Token token = advance();
                Token name = consume(TokenType.IDENTIFIER, "Expected a member name after '.'");
                expr = new Ast.Field(token.line(), expr, name.lexeme());
            } else if (check(TokenType.LPAREN)) {
                Token token = advance();
                List<Ast.Expr> arguments = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    do {
                        arguments.add(expression());
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RPAREN, "Expected ')' after the arguments");
                expr = new Ast.Call(token.line(), expr, arguments);
            } else if (check(TokenType.INCREMENT) || check(TokenType.DECREMENT)) {
                Token token = advance();
                String operator = token.type() == TokenType.INCREMENT ? "++" : "--";
                if (!isAssignable(expr)) {
                    throw error(token, "'" + operator + "' needs a variable or field");
                }
                expr = new Ast.IncDec(token.line(), expr, operator, false);
            } else {
                return expr;
            }
        }
    }

    private Ast.Expr primary() {
        Token token = peek();
        switch (token.type()) {
            case INT_LITERAL -> {
                advance();
                return new Ast.Literal(token.line(), token.literal(), "int");
            }
            case LONG_LITERAL -> {
                advance();
                return new Ast.Literal(token.line(), token.literal(), "long");
            }
            case DOUBLE_LITERAL -> {
                advance();
                return new Ast.Literal(token.line(), token.literal(), "double");
            }
            case STRING_LITERAL -> {
                advance();
                return new Ast.Literal(token.line(), token.literal(), "String");
            }
            case CHAR_LITERAL -> {
                advance();
                return new Ast.Literal(token.line(), token.literal(), "char");
            }
            case TRUE, FALSE -> {
                advance();
                return new Ast.Literal(token.line(), token.literal(), "boolean");
            }
            case NULL -> {
                advance();
                return new Ast.Literal(token.line(), null, "null");
            }
            case THIS -> {
                advance();
                return new Ast.This(token.line());
            }
            case IDENTIFIER -> {
                advance();
                return new Ast.Name(token.line(), token.lexeme());
            }
            case T_STRING -> {
                // Allows the static-call form String.valueOf(...).
                advance();
                return new Ast.Name(token.line(), "String");
            }
            case LPAREN -> {
                advance();
                Ast.Expr inner = expression();
                consume(TokenType.RPAREN, "Expected ')'");
                return inner;
            }
            case NEW -> {
                return newExpression();
            }
            default -> throw error(token, "Expected an expression");
        }
    }

    private Ast.Expr newExpression() {
        int line = consume(TokenType.NEW, "Expected 'new'").line();
        Token typeToken = peek();
        if (!TYPE_KEYWORDS.contains(typeToken.type()) && !check(TokenType.IDENTIFIER)) {
            throw error(typeToken, "Expected a type after 'new'");
        }
        advance();
        StringBuilder typeName = new StringBuilder(typeToken.lexeme());
        while (check(TokenType.DOT) && checkNext(TokenType.IDENTIFIER)) {
            advance();
            typeName.append('.').append(advance().lexeme());
        }
        // Type arguments are erased: new ArrayList<>() and new ArrayList<Integer>() are the same.
        consumeGenericArguments();
        String baseType = typeName.toString();

        // new Node(1) -- object creation.
        if (check(TokenType.LPAREN)) {
            if (TYPE_KEYWORDS.contains(typeToken.type())) {
                throw error(typeToken, "'" + baseType + "' is a primitive type and cannot be "
                        + "created with 'new'");
            }
            advance();
            List<Ast.Expr> arguments = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                do {
                    arguments.add(expression());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "Expected ')' after the constructor arguments");
            return new Ast.NewObject(line, baseType, arguments);
        }

        if (!check(TokenType.LBRACKET)) {
            throw error(peek(),
                    "Expected '(' or '[' after 'new " + baseType + "'");
        }

        // new int[]{...}
        if (checkNext(TokenType.RBRACKET)) {
            int dimensions = 0;
            while (check(TokenType.LBRACKET) && checkNext(TokenType.RBRACKET)) {
                advance();
                advance();
                dimensions++;
            }
            String elementType = baseType + "[]".repeat(dimensions - 1);
            return arrayLiteral(elementType);
        }

        // new int[n], new int[rows][cols], new Node[10]
        List<Ast.Expr> sizes = new ArrayList<>();
        while (check(TokenType.LBRACKET)) {
            advance();
            sizes.add(expression());
            consume(TokenType.RBRACKET, "Expected ']' after the array size");
        }
        return new Ast.NewArray(line, baseType, sizes);
    }

    // ------------------------------------------------------------------ token plumbing

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private boolean checkNext(TokenType type) {
        return current + 1 < tokens.size() && tokens.get(current + 1).type() == type;
    }

    private boolean match(TokenType type) {
        if (!check(type)) {
            return false;
        }
        advance();
        return true;
    }

    private Token advance() {
        Token token = tokens.get(current);
        if (token.type() != TokenType.EOF) {
            current++;
        }
        return token;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private SyntaxException error(Token token, String message) {
        String found = token.type() == TokenType.EOF ? "end of file" : "'" + token.lexeme() + "'";
        return new SyntaxException(message + " but found " + found, token.line());
    }
}
