package com.algolens.execution.interpreter;

import java.util.List;

/**
 * The abstract syntax tree for the supported Java subset.
 *
 * <p>Everything lives in one file on purpose: the {@code sealed} hierarchies can then omit their
 * {@code permits} clauses, and the interpreter's pattern-matching switches are exhaustive without
 * a default branch -- so adding a node type becomes a compile error everywhere it must be handled
 * rather than a silent runtime surprise.
 *
 * <p>Every node carries the 1-based source line it came from. That line travels through the
 * interpreter into each trace event, and is what the editor highlights while stepping.
 */
public final class Ast {

    private Ast() {
    }

    public sealed interface Node {
        int line();
    }

    public sealed interface Expr extends Node {
    }

    public sealed interface Stmt extends Node {
    }

    // ------------------------------------------------------------------ expressions

    /** {@code 42}, {@code 3.5}, {@code "hi"}, {@code 'c'}, {@code true}, {@code null}. */
    public record Literal(int line, Object value, String type) implements Expr {
    }

    /** A bare name: a local, a parameter, an instance field via implicit {@code this}, or a static field. */
    public record Name(int line, String name) implements Expr {
    }

    /** {@code this}. */
    public record This(int line) implements Expr {
    }

    /** {@code arr[i]}. */
    public record Index(int line, Expr target, Expr index) implements Expr {
    }

    /** {@code arr.length}, {@code node.next}, {@code this.data}, {@code Integer.MAX_VALUE}. */
    public record Field(int line, Expr target, String name) implements Expr {
    }

    /** {@code helper(a, b)}, {@code node.insert(3)}, {@code Math.max(x, y)}. */
    public record Call(int line, Expr callee, List<Expr> arguments) implements Expr {
    }

    /** {@code -x}, {@code !flag}, {@code ~bits}, {@code +x}. */
    public record Unary(int line, String operator, Expr operand) implements Expr {
    }

    /** Arithmetic, relational, equality, bitwise and shift operators. */
    public record Binary(int line, Expr left, String operator, Expr right) implements Expr {
    }

    /** {@code &&} and {@code ||}, kept separate because they short circuit. */
    public record Logical(int line, Expr left, String operator, Expr right) implements Expr {
    }

    /** {@code x = v}, {@code arr[i] += v}, {@code node.next = other}. */
    public record Assign(int line, Expr target, String operator, Expr value) implements Expr {
    }

    /** {@code i++}, {@code ++i}, {@code i--}, {@code --i}. */
    public record IncDec(int line, Expr target, String operator, boolean prefix) implements Expr {
    }

    /** {@code c ? a : b}. */
    public record Ternary(int line, Expr condition, Expr thenBranch, Expr elseBranch)
            implements Expr {
    }

    /** {@code (int) x}. */
    public record Cast(int line, String type, Expr expression) implements Expr {
    }

    /** {@code x instanceof Node}. */
    public record InstanceOf(int line, Expr expression, String type) implements Expr {
    }

    /** {@code new int[n]}, {@code new int[rows][cols]}, {@code new Node[10]}. */
    public record NewArray(int line, String elementType, List<Expr> dimensions) implements Expr {
    }

    /** {@code new Node(1)}. */
    public record NewObject(int line, String className, List<Expr> arguments) implements Expr {
    }

    /**
     * {@code {1, 2, 3}} and {@code new int[]{1, 2, 3}}. {@code elementType} may be null when the
     * literal appears as a declaration initialiser; the declared type supplies it at runtime.
     */
    public record ArrayLiteral(int line, String elementType, List<Expr> elements) implements Expr {
    }

    // ------------------------------------------------------------------ statements

    /** One name in a declaration: {@code a}, {@code b = 2}, {@code c[]}. */
    public record Declarator(String name, int extraDimensions, Expr initializer) {
    }

    /** {@code int a = 1, b;} */
    public record VarDecl(int line, String type, List<Declarator> declarators) implements Stmt {
    }

    public record ExprStmt(int line, Expr expression) implements Stmt {
    }

    public record Block(int line, List<Stmt> statements) implements Stmt {
    }

    public record If(int line, Expr condition, Stmt thenBranch, Stmt elseBranch) implements Stmt {
    }

    public record While(int line, Expr condition, Stmt body) implements Stmt {
    }

    public record DoWhile(int line, Stmt body, Expr condition) implements Stmt {
    }

    public record For(int line, List<Stmt> initializers, Expr condition, List<Expr> updates,
            Stmt body) implements Stmt {
    }

    /** {@code for (int v : arr) ...} */
    public record ForEach(int line, String type, String name, Expr iterable, Stmt body)
            implements Stmt {
    }

    public record Return(int line, Expr value) implements Stmt {
    }

    public record Break(int line) implements Stmt {
    }

    public record Continue(int line) implements Stmt {
    }

    /** A stray {@code ;}. */
    public record Empty(int line) implements Stmt {
    }

    /**
     * One group of a switch: its labels and its statements.
     *
     * @param labels    the {@code case} values; empty means {@code default}
     * @param arrowForm true for {@code case 1 -> ...}, which never falls through
     */
    public record SwitchCase(int line, List<Expr> labels, List<Stmt> statements,
            boolean arrowForm) {

        public boolean isDefault() {
            return labels.isEmpty();
        }
    }

    /** {@code switch (x) { case 1: ... }}, in both the colon and arrow forms. */
    public record Switch(int line, Expr selector, List<SwitchCase> cases) implements Stmt {
    }

    /** One {@code catch (Type name) { ... }} clause. */
    public record CatchClause(int line, List<String> types, String name, Block body) {
    }

    /** {@code try { ... } catch (...) { ... } finally { ... }}. */
    public record Try(int line, Block body, List<CatchClause> catches, Block finallyBlock)
            implements Stmt {
    }

    /** {@code throw new IllegalArgumentException("...")}. */
    public record Throw(int line, Expr value) implements Stmt {
    }

    // ------------------------------------------------------------------ declarations

    public record Param(String type, String name) {
    }

    /**
     * A method or a constructor.
     *
     * @param returnType {@code null} marks a constructor; {@code "void"} or a type otherwise
     * @param isStatic   instance methods receive an implicit {@code this}
     */
    public record MethodDecl(int line, String returnType, String name, List<Param> parameters,
            Block body, boolean isStatic) {

        public boolean isConstructor() {
            return returnType == null;
        }

        public int arity() {
            return parameters.size();
        }
    }

    /**
     * A class declaration.
     *
     * <p>Nested classes are flattened by simple name at parse time -- the subset has no inner
     * class scoping, and {@code static class Node} inside {@code Main} is by far the most common
     * way people write a linked list in a single file.
     *
     * @param instanceFields initialised per object, in declaration order
     * @param staticFields   initialised once, shared
     */
    public record ClassDecl(int line, String name, List<VarDecl> instanceFields,
            List<VarDecl> staticFields, List<MethodDecl> methods,
            List<MethodDecl> constructors, List<ClassDecl> nested) {
    }

    /**
     * A parsed submission.
     *
     * @param className   the wrapping class name, or a synthetic one for bare-statement snippets
     * @param classes     every class declared anywhere in the submission, nesting flattened
     * @param fields      static fields, initialised once before the entry point runs
     * @param methods     methods callable without a receiver (the entry class's own methods)
     * @param statements  top-level statements for snippet-style submissions
     * @param entryMethod name of the method to run, or null when {@code statements} is the entry
     */
    public record Program(String className, List<ClassDecl> classes, List<VarDecl> fields,
            List<MethodDecl> methods, List<Stmt> statements, String entryMethod) {
    }
}
