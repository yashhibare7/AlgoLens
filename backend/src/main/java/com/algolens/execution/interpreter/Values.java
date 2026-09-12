package com.algolens.execution.interpreter;

/**
 * Java's value semantics, reimplemented for the subset: numeric promotion, integer division and
 * overflow, string concatenation, and the widening/narrowing rules on assignment.
 *
 * <p>Getting these right matters more than it looks. If {@code (low + high) / 2} silently became
 * floating point, or if {@code int} stopped wrapping, then a student debugging a real integer
 * overflow in their binary search would see the visualizer disagree with {@code javac} -- and the
 * tool would be teaching the wrong thing. So narrowing conversions are rejected exactly as
 * {@code javac} rejects them, rather than being quietly coerced.
 */
public final class Values {

    private Values() {
    }

    // ------------------------------------------------------------------ classification

    public static boolean isNumeric(Object value) {
        return value instanceof Integer || value instanceof Long || value instanceof Double
                || value instanceof Character;
    }

    public static boolean isFloating(Object value) {
        return value instanceof Double;
    }

    public static String typeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Integer) {
            return "int";
        }
        if (value instanceof Long) {
            return "long";
        }
        if (value instanceof Double) {
            return "double";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Character) {
            return "char";
        }
        if (value instanceof String) {
            return "String";
        }
        if (value instanceof ArrayValue array) {
            return array.elementType() + "[]";
        }
        if (value instanceof ObjectValue object) {
            return object.className();
        }
        if (value instanceof ListValue list) {
            return list.kind().displayName();
        }
        if (value instanceof MapValue map) {
            return map.kind().displayName();
        }
        if (value instanceof SetValue set) {
            return set.kind().displayName();
        }
        if (value instanceof BuilderValue) {
            return "StringBuilder";
        }
        if (value instanceof EntryValue) {
            return "Map.Entry";
        }
        if (value instanceof ThrownValue thrown) {
            return thrown.type();
        }
        return value.getClass().getSimpleName();
    }

    public static Object defaultValue(String declaredType) {
        return switch (declaredType) {
            case "int", "short", "byte" -> 0;
            case "long" -> 0L;
            case "double", "float" -> 0.0d;
            case "boolean" -> Boolean.FALSE;
            case "char" -> Character.valueOf('\0');
            default -> null;
        };
    }

    public static boolean isArrayType(String declaredType) {
        return declaredType != null && declaredType.endsWith("[]");
    }

    public static String elementTypeOf(String arrayType) {
        return arrayType.substring(0, arrayType.length() - 2);
    }

    // ------------------------------------------------------------------ truthiness & printing

    public static boolean truth(Object value, int line) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new InterpreterException(
                "Expected a boolean but found " + typeName(value) + ". Java has no truthy values "
                        + "-- write an explicit comparison such as 'x != 0'.",
                line);
    }

    /** How deep {@link #format} renders nested structures before summarising. */
    private static final int DEFAULT_DEPTH = 2;

    /**
     * How a value appears in {@code System.out.println} and in trace messages.
     *
     * <p>One deliberate deviation from real Java: an array prints as {@code [5, 2, 8, 1]} rather
     * than {@code [I@1b6d3586}. The hash form teaches nothing, and a visualizer that prints the
     * contents is what a learner expects.
     *
     * <p>Never calls the user's {@code toString()}: building a trace message must not execute
     * interpreted code, because that would run side effects and burn the step budget just to
     * label a step. See {@code docs/SUPPORTED_JAVA_SUBSET.md}.
     */
    public static String format(Object value) {
        return render(value, DEFAULT_DEPTH);
    }

    /** One level only. Used inside container rendering to stop nesting from running away. */
    public static String shallow(Object value) {
        return render(value, 1);
    }

    /**
     * Depth-limited rendering.
     *
     * <p>The depth budget is what makes this safe rather than merely tidy. {@code list.add(list)}
     * is legal, a node's field can point back at its owner, and a cyclic list is the standard
     * way to test cycle detection -- any of those would send an unbounded renderer into an
     * infinite loop. Past the budget a structure collapses to {@code [...]} or a reference.
     */
    public static String render(Object value, int depth) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ObjectValue object) {
            return depth <= 1 ? object.reference() : object.render();
        }
        if (value instanceof ArrayValue array) {
            return depth <= 0 ? "[...]"
                    : join(java.util.Arrays.asList(array.raw()), depth - 1, "[", "]");
        }
        if (value instanceof ListValue list) {
            return depth <= 0 ? "[...]" : join(list.elements(), depth - 1, "[", "]");
        }
        if (value instanceof SetValue set) {
            return depth <= 0 ? "[...]" : join(set.elements(), depth - 1, "[", "]");
        }
        if (value instanceof MapValue map) {
            if (depth <= 0) {
                return "{...}";
            }
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (java.util.Map.Entry<Object, Object> entry : map.entries().entrySet()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                out.append(render(entry.getKey(), depth - 1)).append('=')
                        .append(render(entry.getValue(), depth - 1));
            }
            return out.append('}').toString();
        }
        if (value instanceof EntryValue entry) {
            return render(entry.key(), depth - 1) + "=" + render(entry.value(), depth - 1);
        }
        if (value instanceof BuilderValue builder) {
            return builder.render();
        }
        if (value instanceof ThrownValue thrown) {
            return thrown.render();
        }
        return String.valueOf(value);
    }

    private static String join(Iterable<Object> values, int depth, String open, String close) {
        StringBuilder out = new StringBuilder(open);
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(render(value, depth));
        }
        return out.append(close).toString();
    }

    /** Strips generic arguments: {@code Map<String, List<Integer>>} becomes {@code Map}. */
    public static String rawType(String declaredType) {
        if (declaredType == null) {
            return null;
        }
        int angle = declaredType.indexOf('<');
        return angle < 0 ? declaredType : declaredType.substring(0, angle).trim();
    }

    /**
     * Drops the package from a raw type name, so {@code java.util.List} resolves the same as
     * {@code List}. {@code Map.Entry} therefore becomes {@code Entry}, which is the name the
     * type tables use for it.
     */
    public static String simpleTypeName(String rawType) {
        if (rawType == null) {
            return null;
        }
        int dot = rawType.lastIndexOf('.');
        return dot < 0 ? rawType : rawType.substring(dot + 1);
    }

    // ------------------------------------------------------------------ arithmetic

    public static Object binary(String operator, Object left, Object right, int line) {
        return switch (operator) {
            case "+" -> add(left, right, line);
            case "-", "*", "/", "%" -> arithmetic(operator, left, right, line);
            case "<", ">", "<=", ">=" -> relational(operator, left, right, line);
            case "==" -> areEqual(left, right);
            case "!=" -> !areEqual(left, right);
            case "&", "|", "^" -> bitwise(operator, left, right, line);
            case "<<", ">>", ">>>" -> shift(operator, left, right, line);
            default -> throw new InterpreterException("Unsupported operator '" + operator + "'",
                    line);
        };
    }

    private static Object add(Object left, Object right, int line) {
        if (left instanceof String || right instanceof String) {
            return format(left) + format(right);
        }
        return arithmetic("+", left, right, line);
    }

    private static Object arithmetic(String operator, Object left, Object right, int line) {
        requireNumeric(operator, left, right, line);
        if (isFloating(left) || isFloating(right)) {
            double a = toDouble(left);
            double b = toDouble(right);
            return switch (operator) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> a / b;
                default -> a % b;
            };
        }
        boolean asLong = left instanceof Long || right instanceof Long;
        long a = toLong(left);
        long b = toLong(right);
        if (("/".equals(operator) || "%".equals(operator)) && b == 0) {
            throw new InterpreterException("/ by zero", line);
        }
        long result = switch (operator) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            default -> a % b;
        };
        // Narrow back to int so overflow wraps exactly as it does on the real JVM.
        return asLong ? (Object) result : (Object) (int) result;
    }

    private static Object relational(String operator, Object left, Object right, int line) {
        requireNumeric(operator, left, right, line);
        int comparison;
        if (isFloating(left) || isFloating(right)) {
            comparison = Double.compare(toDouble(left), toDouble(right));
        } else {
            comparison = Long.compare(toLong(left), toLong(right));
        }
        return switch (operator) {
            case "<" -> comparison < 0;
            case ">" -> comparison > 0;
            case "<=" -> comparison <= 0;
            default -> comparison >= 0;
        };
    }

    public static boolean areEqual(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (isNumeric(left) && isNumeric(right)) {
            if (isFloating(left) || isFloating(right)) {
                return toDouble(left) == toDouble(right);
            }
            return toLong(left) == toLong(right);
        }
        // Reference identity, as in Java. This is what makes 'slow == fast' in a cycle
        // detection loop mean what the algorithm needs it to mean. Note that '==' on two
        // collections is identity even though '.equals()' on them compares contents -- the
        // same trap Java has, and one worth letting learners walk into here.
        if (isReference(left) || isReference(right)) {
            return left == right;
        }
        return left.equals(right);
    }

    /** True for anything whose {@code ==} is identity rather than value comparison. */
    public static boolean isReference(Object value) {
        return value instanceof ArrayValue || value instanceof ObjectValue
                || value instanceof ListValue || value instanceof MapValue
                || value instanceof SetValue || value instanceof BuilderValue;
    }

    private static Object bitwise(String operator, Object left, Object right, int line) {
        if (left instanceof Boolean a && right instanceof Boolean b) {
            return switch (operator) {
                case "&" -> a && b;
                case "|" -> a || b;
                default -> a ^ b;
            };
        }
        requireNumeric(operator, left, right, line);
        if (isFloating(left) || isFloating(right)) {
            throw new InterpreterException(
                    "Operator '" + operator + "' cannot be applied to floating point values", line);
        }
        boolean asLong = left instanceof Long || right instanceof Long;
        long a = toLong(left);
        long b = toLong(right);
        long result = switch (operator) {
            case "&" -> a & b;
            case "|" -> a | b;
            default -> a ^ b;
        };
        return asLong ? (Object) result : (Object) (int) result;
    }

    private static Object shift(String operator, Object left, Object right, int line) {
        requireNumeric(operator, left, right, line);
        if (isFloating(left) || isFloating(right)) {
            throw new InterpreterException(
                    "Operator '" + operator + "' cannot be applied to floating point values", line);
        }
        long amount = toLong(right);
        if (left instanceof Long a) {
            return switch (operator) {
                case "<<" -> a << amount;
                case ">>" -> a >> amount;
                default -> a >>> amount;
            };
        }
        int a = (int) toLong(left);
        int shiftBy = (int) amount;
        return switch (operator) {
            case "<<" -> a << shiftBy;
            case ">>" -> a >> shiftBy;
            default -> a >>> shiftBy;
        };
    }

    public static Object negate(Object value, int line) {
        if (value instanceof Double d) {
            return -d;
        }
        if (value instanceof Long l) {
            return -l;
        }
        if (isNumeric(value)) {
            return -(int) toLong(value);
        }
        throw new InterpreterException("Cannot negate " + typeName(value), line);
    }

    public static Object complement(Object value, int line) {
        if (value instanceof Long l) {
            return ~l;
        }
        if (isNumeric(value) && !isFloating(value)) {
            return ~(int) toLong(value);
        }
        throw new InterpreterException("Cannot apply '~' to " + typeName(value), line);
    }

    private static void requireNumeric(String operator, Object left, Object right, int line) {
        if (!isNumeric(left) || !isNumeric(right)) {
            throw new InterpreterException(
                    "Operator '" + operator + "' cannot be applied to " + typeName(left) + " and "
                            + typeName(right),
                    line);
        }
    }

    public static long toLong(Object value) {
        if (value instanceof Character c) {
            return c;
        }
        return ((Number) value).longValue();
    }

    public static double toDouble(Object value) {
        if (value instanceof Character c) {
            return c;
        }
        return ((Number) value).doubleValue();
    }

    public static int toIndex(Object value, int line) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Character c) {
            return c;
        }
        if (value instanceof Long) {
            throw new InterpreterException(
                    "Array index must be an int, but a long was given (add an (int) cast)", line);
        }
        throw new InterpreterException("Array index must be an int but was " + typeName(value),
                line);
    }

    // ------------------------------------------------------------------ conversions

    /** Applies an explicit {@code (type)} cast. */
    public static Object cast(String targetType, Object value, int line) {
        if (value == null) {
            return null;
        }
        return switch (targetType) {
            case "int" -> isNumeric(value) ? (Object) (int) toLongOrTruncate(value)
                    : failCast(targetType, value, line);
            case "short" -> isNumeric(value) ? (Object) (int) (short) toLongOrTruncate(value)
                    : failCast(targetType, value, line);
            case "byte" -> isNumeric(value) ? (Object) (int) (byte) toLongOrTruncate(value)
                    : failCast(targetType, value, line);
            case "long" -> isNumeric(value) ? (Object) toLongOrTruncate(value)
                    : failCast(targetType, value, line);
            case "double", "float" -> isNumeric(value) ? (Object) toDouble(value)
                    : failCast(targetType, value, line);
            case "char" -> isNumeric(value)
                    ? (Object) Character.valueOf((char) toLongOrTruncate(value))
                    : failCast(targetType, value, line);
            case "boolean" -> value instanceof Boolean ? value : failCast(targetType, value, line);
            case "String" -> value instanceof String ? value : failCast(targetType, value, line);
            default -> failCast(targetType, value, line);
        };
    }

    private static long toLongOrTruncate(Object value) {
        return isFloating(value) ? (long) toDouble(value) : toLong(value);
    }

    private static Object failCast(String targetType, Object value, int line) {
        throw new InterpreterException(
                "Cannot cast " + typeName(value) + " to " + targetType, line);
    }

    /**
     * Assignment conversion. Widening is implicit, narrowing is an error naming the cast that
     * would fix it -- the same trade {@code javac} makes.
     */
    public static Object coerce(String declaredType, Object value, String context, int line) {
        if (isArrayType(declaredType)) {
            if (value == null || value instanceof ArrayValue) {
                return value;
            }
            throw incompatible(context, typeName(value), declaredType, line);
        }
        // Generics are erased for assignment checking: List<Integer> and List<String> are both
        // just List here. The subset does not type-check element types, so declaring the wrong
        // one is not caught -- a documented limitation rather than a silent wrong answer.
        String raw = rawType(declaredType);
        return switch (raw) {
            case "int", "short", "byte" -> {
                if (value instanceof Integer) {
                    yield value;
                }
                if (value instanceof Character c) {
                    yield (int) c.charValue();
                }
                throw narrowing(context, typeName(value), "int", line);
            }
            case "long" -> {
                if (value instanceof Long) {
                    yield value;
                }
                if (value instanceof Integer i) {
                    yield i.longValue();
                }
                if (value instanceof Character c) {
                    yield (long) c.charValue();
                }
                throw narrowing(context, typeName(value), "long", line);
            }
            case "double", "float" -> {
                if (value instanceof Double) {
                    yield value;
                }
                if (isNumeric(value)) {
                    yield toDouble(value);
                }
                throw incompatible(context, typeName(value), "double", line);
            }
            case "boolean" -> {
                if (value instanceof Boolean) {
                    yield value;
                }
                throw incompatible(context, typeName(value), "boolean", line);
            }
            case "char" -> {
                if (value instanceof Character) {
                    yield value;
                }
                throw narrowing(context, typeName(value), "char", line);
            }
            case "String" -> {
                if (value == null || value instanceof String) {
                    yield value;
                }
                throw incompatible(context, typeName(value), "String", line);
            }
            // A reference type: a library collection, or a user-declared class. Both accept
            // null; an instance is accepted only when its type matches, since the subset has no
            // inheritance and so no widening to allow.
            default -> {
                if ("Object".equals(raw) || "var".equals(raw)) {
                    yield value;
                }
                if (value == null) {
                    yield null;
                }
                // Accept both List and java.util.List: an explicit package is still the
                // same type, and rejecting it would fail perfectly ordinary code.
                String simple = simpleTypeName(raw);
                if (CollectionSupport.accepts(simple, value)) {
                    yield value;
                }
                if (CollectionSupport.isKnownType(simple)) {
                    throw incompatible(context, typeName(value), raw, line);
                }
                if (value instanceof ObjectValue object) {
                    if (object.className().equals(simple)) {
                        yield value;
                    }
                    throw incompatible(context, object.className(), raw, line);
                }
                throw incompatible(context, typeName(value), raw, line);
            }
        };
    }

    private static InterpreterException narrowing(String context, String from, String to,
            int line) {
        boolean lossy = "long".equals(from) || "double".equals(from);
        String hint = lossy
                ? " -- possible lossy conversion, add an (" + to + ") cast"
                : "";
        return new InterpreterException(
                "Incompatible types assigning " + from + " to " + to + " " + context + hint, line);
    }

    private static InterpreterException incompatible(String context, String from, String to,
            int line) {
        return new InterpreterException(
                "Incompatible types assigning " + from + " to " + to + " " + context, line);
    }
}
