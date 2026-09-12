package com.algolens.execution.interpreter;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the message shown when a submission has no {@code main}.
 *
 * <p>This exists because the commonest thing anyone pastes into a DSA tool is a LeetCode answer:
 * a bare {@code class Solution} with one method and no entry point. "No main method found" is
 * true but useless -- the user has working code and no idea what the tool wants. So instead of
 * a bare refusal, the message hands back a {@code main} that actually calls their method with
 * sample arguments, ready to paste.
 *
 * <p>The sample values are deliberately neutral rather than clever: the goal is code that
 * compiles and runs on the first try, which the user then edits to their real input.
 */
final class EntryPointHelp {

    private EntryPointHelp() {
    }

    static String message(List<Ast.ClassDecl> classes, List<Ast.MethodDecl> topLevelMethods) {
        Ast.ClassDecl owner = null;
        Ast.MethodDecl target = null;

        for (Ast.ClassDecl declaration : classes) {
            Ast.MethodDecl candidate = pickMethod(declaration.methods());
            if (candidate != null) {
                owner = declaration;
                target = candidate;
                break;
            }
        }
        if (target == null) {
            target = pickMethod(topLevelMethods);
        }

        if (target == null) {
            return "There is nothing to run. Write some statements, or add a "
                    + "'public static void main(String[] args)' method.";
        }

        return """
                No 'main' method found, so AlgoLens does not know what to run.

                Your %s() method looks like the one to call. Paste this into %s:

                %s"""
                .formatted(target.name(),
                        owner == null ? "your code" : "class " + owner.name(),
                        starterMain(owner, target));
    }

    /** Prefers the first method that is not a helper: public, and not static. */
    private static Ast.MethodDecl pickMethod(List<Ast.MethodDecl> methods) {
        Ast.MethodDecl fallback = null;
        for (Ast.MethodDecl method : methods) {
            if (method.isConstructor()) {
                continue;
            }
            if (fallback == null) {
                fallback = method;
            }
            if (!method.isStatic()) {
                return method;
            }
        }
        return fallback;
    }

    /** A runnable {@code main} that declares an argument per parameter and calls the method. */
    private static String starterMain(Ast.ClassDecl owner, Ast.MethodDecl method) {
        StringBuilder body = new StringBuilder();
        List<String> argumentNames = new ArrayList<>(method.parameters().size());

        for (Ast.Param parameter : method.parameters()) {
            argumentNames.add(parameter.name());
            body.append("        ")
                    .append(parameter.type()).append(' ').append(parameter.name())
                    .append(" = ").append(sampleValue(parameter.type())).append(";\n");
        }

        String receiver;
        if (owner == null) {
            receiver = "";
        } else if (method.isStatic()) {
            receiver = owner.name() + ".";
        } else {
            receiver = "new " + owner.name() + "().";
        }

        String call = receiver + method.name() + "(" + String.join(", ", argumentNames) + ")";
        body.append("        ")
                .append("void".equals(method.returnType())
                        ? call + ";"
                        : "System.out.println(" + call + ");")
                .append('\n');

        return "    public static void main(String[] args) {\n" + body + "    }";
    }

    /** A literal that compiles and runs for each supported parameter type. */
    private static String sampleValue(String type) {
        return switch (Values.rawType(type)) {
            case "int", "short", "byte" -> "5";
            case "long" -> "5L";
            case "double", "float" -> "1.5";
            case "boolean" -> "true";
            case "char" -> "'a'";
            case "String" -> "\"hello\"";
            case "int[]", "long[]" -> "{5, 2, 8, 1}";
            case "double[]", "float[]" -> "{1.5, 2.5, 0.5}";
            case "char[]" -> "{'a', 'b', 'c'}";
            case "String[]" -> "{\"a\", \"b\", \"c\"}";
            case "boolean[]" -> "{true, false}";
            case "int[][]" -> "{{1, 2, 3}, {4, 5, 6}}";
            case "List", "ArrayList", "Collection" -> "new ArrayList<>()";
            case "Map", "HashMap" -> "new HashMap<>()";
            case "Set", "HashSet" -> "new HashSet<>()";
            case "Queue", "Deque", "ArrayDeque" -> "new ArrayDeque<>()";
            case "Stack" -> "new Stack<>()";
            case "StringBuilder" -> "new StringBuilder()";
            // A user-declared class or anything unrecognised: null compiles, and the user
            // replaces it with a real instance.
            default -> "null";
        };
    }
}
