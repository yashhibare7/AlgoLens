package com.algolens.execution.interpreter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The standard library slice the subset exposes.
 *
 * <p>Two rules shape what is in here. First, only pure, deterministic functions: a trace has to
 * replay identically from the database days later, so {@code Math.random} and anything touching
 * the clock, filesystem or network is absent by construction rather than by sandbox policy.
 *
 * <p>Second, nothing that would do the user's homework. {@code Arrays.sort} is rejected with a
 * message saying why -- a visualizer whose picture jumps from unsorted to sorted in one opaque
 * step has failed at its only job.
 */
public final class Builtins {

    /** Names that resolve as static namespaces rather than variables. */
    public static final Set<String> NAMESPACES = Set.of(
            "Math", "Integer", "Long", "Double", "Character", "Boolean", "String", "Arrays",
            "System", "Collections", "List", "Objects");

    private Builtins() {
    }

    // ------------------------------------------------------------------ static fields

    public static Object staticField(String namespace, String field, int line) {
        return switch (namespace + "." + field) {
            case "Integer.MAX_VALUE" -> Integer.MAX_VALUE;
            case "Integer.MIN_VALUE" -> Integer.MIN_VALUE;
            case "Long.MAX_VALUE" -> Long.MAX_VALUE;
            case "Long.MIN_VALUE" -> Long.MIN_VALUE;
            case "Double.MAX_VALUE" -> Double.MAX_VALUE;
            case "Double.MIN_VALUE" -> Double.MIN_VALUE;
            case "Math.PI" -> Math.PI;
            case "Math.E" -> Math.E;
            default -> throw new InterpreterException(
                    "Unsupported field '" + namespace + "." + field + "'", line);
        };
    }

    // ------------------------------------------------------------------ static methods

    public static Object callStatic(String namespace, String method, List<Object> args, int line) {
        return switch (namespace) {
            case "Math" -> math(method, args, line);
            case "Integer" -> integer(method, args, line);
            case "Long" -> longs(method, args, line);
            case "Double" -> doubles(method, args, line);
            case "Character" -> characters(method, args, line);
            case "Boolean" -> booleans(method, args, line);
            case "String" -> strings(method, args, line);
            case "Arrays" -> arrays(method, args, line);
            case "Collections", "List", "Objects" ->
                    CollectionSupport.callStatic(namespace, method, args, line);
            default -> throw new InterpreterException(
                    "Unsupported call '" + namespace + "." + method + "'", line);
        };
    }

    private static Object math(String method, List<Object> args, int line) {
        if ("random".equals(method)) {
            throw new InterpreterException(
                    "Math.random() is not available: a visualised run has to replay identically "
                            + "every time it is opened from history. Use fixed input values.",
                    line);
        }
        if (args.size() == 1) {
            Object a = arg(args, 0, method, line);
            boolean floating = Values.isFloating(a);
            return switch (method) {
                case "abs" -> floating ? (Object) Math.abs(Values.toDouble(a))
                        : a instanceof Long l ? (Object) Math.abs(l)
                                : (Object) Math.abs((int) Values.toLong(a));
                case "sqrt" -> Math.sqrt(Values.toDouble(a));
                case "cbrt" -> Math.cbrt(Values.toDouble(a));
                case "floor" -> Math.floor(Values.toDouble(a));
                case "ceil" -> Math.ceil(Values.toDouble(a));
                case "round" -> floating ? (Object) (int) Math.round(Values.toDouble(a)) : a;
                case "log" -> Math.log(Values.toDouble(a));
                case "log10" -> Math.log10(Values.toDouble(a));
                case "exp" -> Math.exp(Values.toDouble(a));
                case "signum" -> Math.signum(Values.toDouble(a));
                default -> throw unsupported("Math", method, line);
            };
        }
        if (args.size() == 2) {
            Object a = arg(args, 0, method, line);
            Object b = arg(args, 1, method, line);
            boolean floating = Values.isFloating(a) || Values.isFloating(b);
            boolean asLong = a instanceof Long || b instanceof Long;
            return switch (method) {
                case "max" -> floating ? (Object) Math.max(Values.toDouble(a), Values.toDouble(b))
                        : asLong ? (Object) Math.max(Values.toLong(a), Values.toLong(b))
                                : (Object) Math.max((int) Values.toLong(a),
                                        (int) Values.toLong(b));
                case "min" -> floating ? (Object) Math.min(Values.toDouble(a), Values.toDouble(b))
                        : asLong ? (Object) Math.min(Values.toLong(a), Values.toLong(b))
                                : (Object) Math.min((int) Values.toLong(a),
                                        (int) Values.toLong(b));
                case "pow" -> Math.pow(Values.toDouble(a), Values.toDouble(b));
                case "hypot" -> Math.hypot(Values.toDouble(a), Values.toDouble(b));
                default -> throw unsupported("Math", method, line);
            };
        }
        throw unsupported("Math", method, line);
    }

    private static Object integer(String method, List<Object> args, int line) {
        return switch (method) {
            case "parseInt" -> parseIntOrFail(text(args, 0, line), line);
            case "valueOf" -> args.get(0) instanceof String s ? parseIntOrFail(s, line)
                    : (int) Values.toLong(args.get(0));
            case "toString" -> Values.format(args.get(0));
            case "compare" -> Integer.compare((int) Values.toLong(args.get(0)),
                    (int) Values.toLong(args.get(1)));
            case "max" -> Math.max((int) Values.toLong(args.get(0)),
                    (int) Values.toLong(args.get(1)));
            case "min" -> Math.min((int) Values.toLong(args.get(0)),
                    (int) Values.toLong(args.get(1)));
            default -> throw unsupported("Integer", method, line);
        };
    }

    private static Object longs(String method, List<Object> args, int line) {
        return switch (method) {
            case "parseLong" -> {
                try {
                    yield Long.parseLong(text(args, 0, line).trim());
                } catch (NumberFormatException e) {
                    throw new InterpreterException(
                            "NumberFormatException: For input string: \"" + text(args, 0, line)
                                    + "\"",
                            line);
                }
            }
            case "valueOf" -> Values.toLong(args.get(0));
            case "toString" -> Values.format(args.get(0));
            default -> throw unsupported("Long", method, line);
        };
    }

    private static Object doubles(String method, List<Object> args, int line) {
        return switch (method) {
            case "parseDouble" -> {
                try {
                    yield Double.parseDouble(text(args, 0, line).trim());
                } catch (NumberFormatException e) {
                    throw new InterpreterException(
                            "NumberFormatException: For input string: \"" + text(args, 0, line)
                                    + "\"",
                            line);
                }
            }
            case "valueOf" -> Values.toDouble(args.get(0));
            case "toString" -> Values.format(args.get(0));
            default -> throw unsupported("Double", method, line);
        };
    }

    private static Object characters(String method, List<Object> args, int line) {
        char c = requireChar(args, 0, line);
        return switch (method) {
            case "isDigit" -> Character.isDigit(c);
            case "isLetter" -> Character.isLetter(c);
            case "isLetterOrDigit" -> Character.isLetterOrDigit(c);
            case "isWhitespace" -> Character.isWhitespace(c);
            case "isUpperCase" -> Character.isUpperCase(c);
            case "isLowerCase" -> Character.isLowerCase(c);
            case "toUpperCase" -> Character.toUpperCase(c);
            case "toLowerCase" -> Character.toLowerCase(c);
            case "getNumericValue" -> Character.getNumericValue(c);
            default -> throw unsupported("Character", method, line);
        };
    }

    private static Object booleans(String method, List<Object> args, int line) {
        if ("parseBoolean".equals(method)) {
            return Boolean.parseBoolean(text(args, 0, line));
        }
        throw unsupported("Boolean", method, line);
    }

    private static Object strings(String method, List<Object> args, int line) {
        switch (method) {
            case "valueOf" -> {
                return Values.format(args.get(0));
            }
            case "join" -> {
                String separator = text(args, 0, line);
                List<Object> parts = args.size() == 2
                        && (args.get(1) instanceof ListValue || args.get(1) instanceof SetValue)
                                ? CollectionSupport.orderedElements(args.get(1))
                                : args.subList(1, args.size());
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < parts.size(); i++) {
                    if (i > 0) {
                        out.append(separator);
                    }
                    out.append(Values.format(parts.get(i)));
                }
                return out.toString();
            }
            case "format" -> {
                return format(text(args, 0, line), args.subList(1, args.size()), line);
            }
            default -> throw unsupported("String", method, line);
        }
    }

    /**
     * {@code String.format} / {@code printf}, delegating to the real formatter.
     *
     * <p>Interpreted values are converted first: a number stays a number so {@code %d} and
     * {@code %.2f} behave exactly as they do in Java, and anything structural becomes its
     * rendered text so {@code %s} never leaks an interpreter internal.
     */
    static String format(String pattern, List<Object> args, int line) {
        Object[] converted = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            Object value = args.get(i);
            converted[i] = Values.isNumeric(value) || value instanceof Boolean
                    ? value
                    : Values.format(value);
        }
        try {
            return String.format(pattern, converted);
        } catch (java.util.IllegalFormatException e) {
            throw new InterpreterException(
                    "IllegalArgumentException: bad format string \"" + pattern + "\" ("
                            + e.getMessage() + ")",
                    line);
        }
    }

    private static Object arrays(String method, List<Object> args, int line) {
        return switch (method) {
            // These used to be refused, on the reasoning that sorting in one invisible step
            // defeats the point of a visualizer. That was the wrong call: plenty of correct
            // code sorts as a *precondition* (sort, then two-pointer), and refusing it just
            // makes working Java fail. They run, and the trace labels them as a single library
            // step so it is obvious the intermediate work was not shown.
            case "sort" -> {
                ArrayValue array = requireArray(args, 0, line);
                Object[] values = array.raw();
                if (args.size() >= 3) {
                    int from = (int) Values.toLong(args.get(1));
                    int to = (int) Values.toLong(args.get(2));
                    if (from < 0 || to > values.length || from > to) {
                        throw new InterpreterException(
                                "ArrayIndexOutOfBoundsException: range [" + from + ", " + to
                                        + ") for length " + values.length,
                                line);
                    }
                    Arrays.sort(values, from, to, NaturalOrder.INSTANCE);
                } else {
                    Arrays.sort(values, NaturalOrder.INSTANCE);
                }
                yield null;
            }
            case "binarySearch" -> {
                ArrayValue array = requireArray(args, 0, line);
                int found = Arrays.binarySearch(array.raw(), args.get(1), NaturalOrder.INSTANCE);
                yield found;
            }
            case "asList" -> {
                ListValue list = new ListValue(ListValue.Kind.ARRAY_LIST);
                // Arrays.asList(arr) with a single array argument is read as "a list of these
                // elements", which is what people mean, rather than Java's literal
                // List<int[]> of one element.
                if (args.size() == 1 && args.get(0) instanceof ArrayValue array) {
                    list.elements().addAll(Arrays.asList(array.raw()));
                } else {
                    list.elements().addAll(args);
                }
                yield list;
            }
            case "toString", "deepToString" -> requireArray(args, 0, line).render();
            case "fill" -> {
                ArrayValue array = requireArray(args, 0, line);
                Object value = Values.coerce(array.elementType(), args.get(1),
                        "in Arrays.fill", line);
                Arrays.fill(array.raw(), value);
                yield null;
            }
            case "copyOf" -> {
                ArrayValue array = requireArray(args, 0, line);
                int length = (int) Values.toLong(args.get(1));
                if (length < 0) {
                    throw new InterpreterException("Negative array size: " + length, line);
                }
                Object[] copy = new Object[length];
                Arrays.fill(copy, Values.defaultValue(array.elementType()));
                System.arraycopy(array.raw(), 0, copy, 0, Math.min(length, array.length()));
                yield new ArrayValue(array.elementType(), copy);
            }
            case "copyOfRange" -> {
                ArrayValue array = requireArray(args, 0, line);
                int from = (int) Values.toLong(args.get(1));
                int to = (int) Values.toLong(args.get(2));
                if (from < 0 || to > array.length() || from > to) {
                    throw new InterpreterException(
                            "Invalid range [" + from + ", " + to + ") for length "
                                    + array.length(),
                            line);
                }
                Object[] copy = new Object[to - from];
                System.arraycopy(array.raw(), from, copy, 0, to - from);
                yield new ArrayValue(array.elementType(), copy);
            }
            case "equals" -> Arrays.equals(requireArray(args, 0, line).raw(),
                    requireArray(args, 1, line).raw());
            default -> throw unsupported("Arrays", method, line);
        };
    }

    // ------------------------------------------------------------------ instance methods

    public static Object callInstance(Object receiver, String method, List<Object> args, int line) {
        if (receiver == null) {
            throw new InterpreterException(
                    "NullPointerException: cannot call '" + method + "()' on null", line);
        }
        if (receiver instanceof String s) {
            return stringMethod(s, method, args, line);
        }
        if (receiver instanceof ArrayValue) {
            throw new InterpreterException(
                    "Arrays have no method '" + method + "()'. Use arr.length for the size.", line);
        }
        throw new InterpreterException(
                "Type " + Values.typeName(receiver) + " has no method '" + method + "()'", line);
    }

    private static Object stringMethod(String receiver, String method, List<Object> args,
            int line) {
        try {
            return switch (method) {
                case "length" -> receiver.length();
                case "isEmpty" -> receiver.isEmpty();
                case "isBlank" -> receiver.isBlank();
                case "charAt" -> receiver.charAt((int) Values.toLong(args.get(0)));
                case "equals" -> receiver.equals(args.get(0));
                case "equalsIgnoreCase" -> args.get(0) instanceof String other
                        && receiver.equalsIgnoreCase(other);
                case "compareTo" -> receiver.compareTo(text(args, 0, line));
                case "contains" -> receiver.contains(text(args, 0, line));
                case "startsWith" -> receiver.startsWith(text(args, 0, line));
                case "endsWith" -> receiver.endsWith(text(args, 0, line));
                case "indexOf" -> args.get(0) instanceof Character c
                        ? receiver.indexOf(c)
                        : receiver.indexOf(text(args, 0, line));
                case "lastIndexOf" -> args.get(0) instanceof Character c
                        ? receiver.lastIndexOf(c)
                        : receiver.lastIndexOf(text(args, 0, line));
                case "substring" -> args.size() == 1
                        ? receiver.substring((int) Values.toLong(args.get(0)))
                        : receiver.substring((int) Values.toLong(args.get(0)),
                                (int) Values.toLong(args.get(1)));
                case "toUpperCase" -> receiver.toUpperCase();
                case "toLowerCase" -> receiver.toLowerCase();
                case "trim" -> receiver.trim();
                case "strip" -> receiver.strip();
                case "replace" -> receiver.replace(Values.format(args.get(0)),
                        Values.format(args.get(1)));
                case "concat" -> receiver.concat(text(args, 0, line));
                case "toCharArray" -> {
                    char[] chars = receiver.toCharArray();
                    Object[] boxed = new Object[chars.length];
                    for (int i = 0; i < chars.length; i++) {
                        boxed[i] = chars[i];
                    }
                    yield new ArrayValue("char", boxed);
                }
                case "split" -> {
                    // Literal separator only: regex behaviour would be surprising in a teaching
                    // tool, and the subset has no Pattern anyway.
                    String[] parts = receiver.split(java.util.regex.Pattern.quote(
                            text(args, 0, line)), -1);
                    yield new ArrayValue("String", Arrays.copyOf(parts, parts.length, Object[].class));
                }
                default -> throw unsupported("String", method, line);
            };
        } catch (StringIndexOutOfBoundsException e) {
            throw new InterpreterException("StringIndexOutOfBoundsException: " + e.getMessage(),
                    line);
        } catch (IndexOutOfBoundsException e) {
            throw new InterpreterException("IndexOutOfBoundsException: " + e.getMessage(), line);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Object arg(List<Object> args, int index, String method, int line) {
        Object value = args.get(index);
        if (!Values.isNumeric(value)) {
            throw new InterpreterException(
                    "Math." + method + "() expects a number but got " + Values.typeName(value),
                    line);
        }
        return value;
    }

    private static int parseIntOrFail(String raw, int line) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new InterpreterException(
                    "NumberFormatException: For input string: \"" + raw + "\"", line);
        }
    }

    private static String text(List<Object> args, int index, int line) {
        Object value = args.get(index);
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Character c) {
            return String.valueOf(c);
        }
        throw new InterpreterException("Expected a String but got " + Values.typeName(value), line);
    }

    private static char requireChar(List<Object> args, int index, int line) {
        Object value = args.get(index);
        if (value instanceof Character c) {
            return c;
        }
        if (value instanceof Integer i) {
            return (char) i.intValue();
        }
        throw new InterpreterException("Expected a char but got " + Values.typeName(value), line);
    }

    private static ArrayValue requireArray(List<Object> args, int index, int line) {
        Object value = args.get(index);
        if (value instanceof ArrayValue array) {
            return array;
        }
        throw new InterpreterException("Expected an array but got " + Values.typeName(value), line);
    }

    private static InterpreterException unsupported(String namespace, String method, int line) {
        return new InterpreterException(
                namespace + "." + method + "() is not part of the supported subset yet", line);
    }
}
