package com.algolens.execution.interpreter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The collections part of the standard library: {@code ArrayList}, {@code LinkedList},
 * {@code Stack}, {@code ArrayDeque}, {@code PriorityQueue}, the {@code Map} and {@code Set}
 * families, and {@code StringBuilder}.
 *
 * <p>These are implemented as interpreter built-ins rather than as interpreted Java source. The
 * trade-off is deliberate: built-ins are fast and cannot themselves be stepped into, whereas
 * shipping them as source would let a learner step into {@code ArrayList.add} -- a genuinely
 * nice feature, and the plan on the roadmap -- at the cost of every list operation costing
 * dozens of steps from the budget. Built-ins first, because they are what unblock the
 * algorithms.
 *
 * <p><b>Method semantics are kind-aware and that is the point.</b> {@code push} appends on a
 * {@code Stack} and prepends on an {@code ArrayDeque}; {@code poll} takes the smallest element
 * from a {@code PriorityQueue} and the first from a {@code Queue}. A visualizer that got those
 * backwards would be actively misleading, so each is dispatched on the declared type and
 * covered by tests.
 */
public final class CollectionSupport {

    /** Names usable as static call targets, in addition to {@link Builtins#NAMESPACES}. */
    public static final Set<String> NAMESPACES = Set.of("Collections", "List", "Objects");

    private CollectionSupport() {
    }

    /**
     * What a collection call did, so the caller can record the right trace annotation.
     *
     * @param index the element position involved, or -1 when the operation was structural
     */
    public record Outcome(Object value, int index, boolean write, boolean touched) {

        static Outcome plain(Object value) {
            return new Outcome(value, -1, false, false);
        }

        static Outcome read(Object value, int index) {
            return new Outcome(value, index, false, true);
        }

        static Outcome wrote(Object value, int index) {
            return new Outcome(value, index, true, true);
        }
    }

    // ------------------------------------------------------------------ construction

    public static boolean isConstructible(String rawType) {
        return switch (rawType) {
            case "ArrayList", "LinkedList", "Stack", "ArrayDeque", "PriorityQueue",
                    "HashMap", "LinkedHashMap", "TreeMap",
                    "HashSet", "LinkedHashSet", "TreeSet",
                    "StringBuilder", "StringBuffer" -> true;
            default -> false;
        };
    }

    public static Object construct(String rawType, List<Object> arguments, int line) {
        Object seed = arguments.isEmpty() ? null : arguments.get(0);

        switch (rawType) {
            case "ArrayList", "LinkedList", "Stack", "ArrayDeque", "PriorityQueue" -> {
                ListValue.Kind kind = switch (rawType) {
                    case "LinkedList" -> ListValue.Kind.LINKED_LIST;
                    case "Stack" -> ListValue.Kind.STACK;
                    case "ArrayDeque" -> ListValue.Kind.ARRAY_DEQUE;
                    case "PriorityQueue" -> ListValue.Kind.PRIORITY_QUEUE;
                    default -> ListValue.Kind.ARRAY_LIST;
                };
                ListValue list = new ListValue(kind);
                // new ArrayList<>(10) is a capacity hint and is ignored, as it is in Java.
                if (seed != null && !Values.isNumeric(seed)) {
                    if (seed instanceof ListValue || seed instanceof SetValue) {
                        for (Object element : iterate(seed, line)) {
                            list.addOrdered(element);
                        }
                    } else {
                        throw new InterpreterException(
                                "new " + rawType + "(...) accepts a capacity or another "
                                        + "collection, but got " + Values.typeName(seed)
                                        + ". Custom comparators need lambdas, which are not in "
                                        + "the supported subset yet.",
                                line);
                    }
                }
                return list;
            }
            case "HashMap", "LinkedHashMap", "TreeMap" -> {
                MapValue.Kind kind = switch (rawType) {
                    case "LinkedHashMap" -> MapValue.Kind.LINKED_HASH_MAP;
                    case "TreeMap" -> MapValue.Kind.TREE_MAP;
                    default -> MapValue.Kind.HASH_MAP;
                };
                MapValue map = new MapValue(kind);
                if (seed instanceof MapValue other) {
                    map.entries().putAll(other.entries());
                }
                return map;
            }
            case "HashSet", "LinkedHashSet", "TreeSet" -> {
                SetValue.Kind kind = switch (rawType) {
                    case "LinkedHashSet" -> SetValue.Kind.LINKED_HASH_SET;
                    case "TreeSet" -> SetValue.Kind.TREE_SET;
                    default -> SetValue.Kind.HASH_SET;
                };
                SetValue set = new SetValue(kind);
                if (seed != null && !Values.isNumeric(seed)) {
                    for (Object element : iterate(seed, line)) {
                        set.elements().add(element);
                    }
                }
                return set;
            }
            case "StringBuilder", "StringBuffer" -> {
                if (seed instanceof String text) {
                    return new BuilderValue(text);
                }
                return new BuilderValue();
            }
            default -> throw new InterpreterException(
                    "Cannot create a " + rawType, line);
        }
    }

    // ------------------------------------------------------------------ type checking

    /** Whether a value may be assigned to a variable of this declared raw type. */
    public static boolean accepts(String rawDeclaredType, Object value) {
        return switch (rawDeclaredType) {
            case "List", "Collection", "Iterable", "AbstractList" ->
                    value instanceof ListValue;
            case "ArrayList" -> value instanceof ListValue list
                    && list.kind() == ListValue.Kind.ARRAY_LIST;
            case "LinkedList" -> value instanceof ListValue list
                    && list.kind() == ListValue.Kind.LINKED_LIST;
            case "Stack" -> value instanceof ListValue list
                    && list.kind() == ListValue.Kind.STACK;
            // A LinkedList is a legal Deque and Queue in Java, so both accept either.
            case "Deque", "Queue", "ArrayDeque" -> value instanceof ListValue list
                    && (list.kind() == ListValue.Kind.ARRAY_DEQUE
                            || list.kind() == ListValue.Kind.LINKED_LIST
                            || list.kind() == ListValue.Kind.PRIORITY_QUEUE);
            case "PriorityQueue" -> value instanceof ListValue list
                    && list.kind() == ListValue.Kind.PRIORITY_QUEUE;
            case "Map", "AbstractMap" -> value instanceof MapValue;
            case "HashMap" -> value instanceof MapValue map
                    && map.kind() == MapValue.Kind.HASH_MAP;
            case "LinkedHashMap" -> value instanceof MapValue map
                    && map.kind() == MapValue.Kind.LINKED_HASH_MAP;
            case "TreeMap", "SortedMap", "NavigableMap" -> value instanceof MapValue map
                    && map.kind() == MapValue.Kind.TREE_MAP;
            case "Set" -> value instanceof SetValue;
            case "HashSet" -> value instanceof SetValue set
                    && set.kind() == SetValue.Kind.HASH_SET;
            case "LinkedHashSet" -> value instanceof SetValue set
                    && set.kind() == SetValue.Kind.LINKED_HASH_SET;
            case "TreeSet", "SortedSet", "NavigableSet" -> value instanceof SetValue set
                    && set.kind() == SetValue.Kind.TREE_SET;
            case "StringBuilder", "StringBuffer", "CharSequence" ->
                    value instanceof BuilderValue || value instanceof String;
            case "Map.Entry", "Entry" -> value instanceof EntryValue;
            case "Integer" -> value instanceof Integer;
            case "Long" -> value instanceof Long;
            case "Double", "Float" -> value instanceof Double;
            case "Boolean" -> value instanceof Boolean;
            case "Character" -> value instanceof Character;
            case "Number" -> Values.isNumeric(value);
            case "Exception", "RuntimeException", "Throwable", "Error" ->
                    value instanceof ThrownValue;
            default -> false;
        };
    }

    /** Declared types that are collection-shaped, so a mismatch can be explained properly. */
    public static boolean isKnownType(String rawDeclaredType) {
        return switch (rawDeclaredType) {
            case "List", "Collection", "Iterable", "AbstractList", "ArrayList", "LinkedList",
                    "Stack", "Deque", "Queue", "ArrayDeque", "PriorityQueue", "Map", "AbstractMap",
                    "HashMap", "LinkedHashMap", "TreeMap", "SortedMap", "NavigableMap", "Set",
                    "HashSet", "LinkedHashSet", "TreeSet", "SortedSet", "NavigableSet",
                    "StringBuilder", "StringBuffer", "CharSequence", "Map.Entry", "Entry",
                    "Integer", "Long", "Double", "Float", "Boolean", "Character", "Number",
                    "Exception", "RuntimeException", "Throwable", "Error" -> true;
            default -> false;
        };
    }

    public static boolean isContainer(Object value) {
        return value instanceof ListValue || value instanceof MapValue
                || value instanceof SetValue;
    }

    // ------------------------------------------------------------------ iteration

    /** The elements a for-each loop should walk, in the collection's own order. */
    public static Iterable<Object> iterate(Object value, int line) {
        if (value instanceof ListValue list) {
            return new ArrayList<>(list.elements());
        }
        if (value instanceof SetValue set) {
            return new ArrayList<>(set.elements());
        }
        if (value instanceof MapValue) {
            throw new InterpreterException(
                    "A Map cannot be iterated directly. Use map.keySet(), map.values() or "
                            + "map.entrySet().",
                    line);
        }
        throw new InterpreterException(
                "Cannot iterate over " + Values.typeName(value), line);
    }

    // ------------------------------------------------------------------ instance methods

    public static Outcome invoke(Object receiver, String method, List<Object> arguments,
            int line) {
        if (receiver instanceof ListValue list) {
            return list(list, method, arguments, line);
        }
        if (receiver instanceof MapValue map) {
            return map(map, method, arguments, line);
        }
        if (receiver instanceof SetValue set) {
            return set(set, method, arguments, line);
        }
        if (receiver instanceof BuilderValue builder) {
            return builder(builder, method, arguments, line);
        }
        if (receiver instanceof EntryValue entry) {
            return switch (method) {
                case "getKey" -> Outcome.plain(entry.key());
                case "getValue" -> Outcome.plain(entry.value());
                case "toString" -> Outcome.plain(entry.render());
                case "setValue" -> throw new InterpreterException(
                        "Map.Entry.setValue() is not supported. Write back with map.put(...) "
                                + "instead.",
                        line);
                default -> throw unsupported("Map.Entry", method, line);
            };
        }
        if (receiver instanceof ThrownValue thrown) {
            return switch (method) {
                case "getMessage", "getLocalizedMessage" -> Outcome.plain(thrown.message());
                case "toString" -> Outcome.plain(thrown.render());
                default -> throw unsupported(thrown.type(), method, line);
            };
        }
        throw new InterpreterException(
                "Type " + Values.typeName(receiver) + " has no method '" + method + "()'", line);
    }

    // ------------------------------------------------------------------ lists, deques, stacks

    private static Outcome list(ListValue list, String method, List<Object> arguments, int line) {
        List<Object> elements = list.elements();
        ListValue.Kind kind = list.kind();

        switch (method) {
            case "size" -> {
                return Outcome.plain(elements.size());
            }
            case "isEmpty" -> {
                return Outcome.plain(elements.isEmpty());
            }
            case "empty" -> {
                requireKind(kind, method, line, ListValue.Kind.STACK);
                return Outcome.plain(elements.isEmpty());
            }
            case "clear" -> {
                elements.clear();
                return new Outcome(null, -1, true, false);
            }
            case "toString" -> {
                return Outcome.plain(list.render());
            }
            case "contains" -> {
                int index = indexOf(elements, arguments.get(0));
                return index >= 0 ? Outcome.read(true, index) : Outcome.plain(false);
            }
            case "indexOf" -> {
                return Outcome.plain(indexOf(elements, arguments.get(0)));
            }
            case "lastIndexOf" -> {
                for (int i = elements.size() - 1; i >= 0; i--) {
                    if (Objects.equals(elements.get(i), arguments.get(0))) {
                        return Outcome.read(i, i);
                    }
                }
                return Outcome.plain(-1);
            }
            case "equals" -> {
                return Outcome.plain(arguments.get(0) instanceof ListValue other
                        && elements.equals(other.elements()));
            }
            case "get" -> {
                requireIndexed(kind, method, line);
                int index = checkIndex(arguments.get(0), elements.size(), line, false);
                return Outcome.read(elements.get(index), index);
            }
            case "set" -> {
                requireIndexed(kind, method, line);
                int index = checkIndex(arguments.get(0), elements.size(), line, false);
                Object previous = elements.set(index, arguments.get(1));
                return Outcome.wrote(previous, index);
            }
            case "add", "addLast", "offerLast" -> {
                if (arguments.size() == 2) {
                    requireIndexed(kind, "add(index, element)", line);
                    int index = checkIndex(arguments.get(0), elements.size(), line, true);
                    elements.add(index, arguments.get(1));
                    return Outcome.wrote(true, index);
                }
                if (kind == ListValue.Kind.PRIORITY_QUEUE) {
                    list.addOrdered(arguments.get(0));
                    return Outcome.wrote(true, indexOf(elements, arguments.get(0)));
                }
                elements.add(arguments.get(0));
                return Outcome.wrote(true, elements.size() - 1);
            }
            case "addFirst", "offerFirst" -> {
                requireDeque(kind, method, line);
                elements.add(0, arguments.get(0));
                return Outcome.wrote(true, 0);
            }
            case "offer" -> {
                requireQueue(kind, method, line);
                if (kind == ListValue.Kind.PRIORITY_QUEUE) {
                    list.addOrdered(arguments.get(0));
                    return Outcome.wrote(true, indexOf(elements, arguments.get(0)));
                }
                elements.add(arguments.get(0));
                return Outcome.wrote(true, elements.size() - 1);
            }
            case "addAll" -> {
                for (Object element : iterate(arguments.get(0), line)) {
                    list.addOrdered(element);
                }
                return new Outcome(true, -1, true, false);
            }
            case "push" -> {
                // The one place kinds genuinely diverge: Stack pushes onto the end,
                // ArrayDeque pushes onto the front.
                if (kind == ListValue.Kind.STACK) {
                    elements.add(arguments.get(0));
                    return Outcome.wrote(null, elements.size() - 1);
                }
                requireDeque(kind, method, line);
                elements.add(0, arguments.get(0));
                return Outcome.wrote(null, 0);
            }
            case "pop" -> {
                requireNotEmpty(elements, kind, line);
                if (kind == ListValue.Kind.STACK) {
                    return Outcome.wrote(elements.remove(elements.size() - 1), elements.size());
                }
                requireDeque(kind, method, line);
                return Outcome.wrote(elements.remove(0), 0);
            }
            case "peek" -> {
                if (elements.isEmpty()) {
                    // Stack.peek() throws on empty; Queue/Deque.peek() returns null.
                    if (kind == ListValue.Kind.STACK) {
                        throw new InterpreterException(
                                "EmptyStackException: peek() on an empty Stack", line);
                    }
                    return Outcome.plain(null);
                }
                int index = kind == ListValue.Kind.STACK ? elements.size() - 1 : 0;
                return Outcome.read(elements.get(index), index);
            }
            case "peekFirst", "element", "getFirst" -> {
                if (elements.isEmpty()) {
                    if ("peekFirst".equals(method)) {
                        return Outcome.plain(null);
                    }
                    throw new InterpreterException(
                            "NoSuchElementException: the collection is empty", line);
                }
                return Outcome.read(elements.get(0), 0);
            }
            case "peekLast", "getLast" -> {
                if (elements.isEmpty()) {
                    if ("peekLast".equals(method)) {
                        return Outcome.plain(null);
                    }
                    throw new InterpreterException(
                            "NoSuchElementException: the collection is empty", line);
                }
                return Outcome.read(elements.get(elements.size() - 1), elements.size() - 1);
            }
            case "poll", "pollFirst", "removeFirst" -> {
                if (elements.isEmpty()) {
                    if ("removeFirst".equals(method)) {
                        throw new InterpreterException(
                                "NoSuchElementException: the collection is empty", line);
                    }
                    return Outcome.plain(null);
                }
                return Outcome.wrote(elements.remove(0), 0);
            }
            case "pollLast", "removeLast" -> {
                if (elements.isEmpty()) {
                    if ("removeLast".equals(method)) {
                        throw new InterpreterException(
                                "NoSuchElementException: the collection is empty", line);
                    }
                    return Outcome.plain(null);
                }
                int last = elements.size() - 1;
                return Outcome.wrote(elements.remove(last), last);
            }
            case "remove" -> {
                if (arguments.isEmpty()) {
                    requireNotEmpty(elements, kind, line);
                    return Outcome.wrote(elements.remove(0), 0);
                }
                Object argument = arguments.get(0);
                // List.remove(int) removes by index; remove(Object) removes by value. Java
                // resolves on the static type, and this matches for the types the subset has.
                if (argument instanceof Integer index && isIndexed(kind)) {
                    int position = checkIndex(index, elements.size(), line, false);
                    return Outcome.wrote(elements.remove(position), position);
                }
                int position = indexOf(elements, argument);
                if (position < 0) {
                    return Outcome.plain(false);
                }
                elements.remove(position);
                return Outcome.wrote(true, position);
            }
            case "sort" -> {
                elements.sort(NaturalOrder.INSTANCE);
                return new Outcome(null, -1, true, false);
            }
            case "iterator", "stream", "forEach" -> throw new InterpreterException(
                    "'" + method + "()' needs lambdas or iterators, which are not in the "
                            + "supported subset yet. Use a for-each loop instead.",
                    line);
            default -> throw unsupported(kind.displayName(), method, line);
        }
    }

    private static boolean isIndexed(ListValue.Kind kind) {
        return kind == ListValue.Kind.ARRAY_LIST || kind == ListValue.Kind.LINKED_LIST
                || kind == ListValue.Kind.STACK;
    }

    private static void requireIndexed(ListValue.Kind kind, String method, int line) {
        if (!isIndexed(kind)) {
            throw new InterpreterException(
                    kind.displayName() + " has no '" + method
                            + "' -- it is not index addressable. Declare an ArrayList if you "
                            + "need positional access.",
                    line);
        }
    }

    private static void requireDeque(ListValue.Kind kind, String method, int line) {
        if (kind != ListValue.Kind.ARRAY_DEQUE && kind != ListValue.Kind.LINKED_LIST) {
            throw new InterpreterException(
                    kind.displayName() + " has no '" + method + "()'. Declare an ArrayDeque or "
                            + "LinkedList for double-ended access.",
                    line);
        }
    }

    private static void requireQueue(ListValue.Kind kind, String method, int line) {
        if (kind == ListValue.Kind.ARRAY_LIST || kind == ListValue.Kind.STACK) {
            throw new InterpreterException(
                    kind.displayName() + " has no '" + method + "()'. Declare a Queue, "
                            + "ArrayDeque or PriorityQueue.",
                    line);
        }
    }

    private static void requireKind(ListValue.Kind actual, String method, int line,
            ListValue.Kind expected) {
        if (actual != expected) {
            throw new InterpreterException(
                    actual.displayName() + " has no '" + method + "()'", line);
        }
    }

    private static void requireNotEmpty(List<Object> elements, ListValue.Kind kind, int line) {
        if (elements.isEmpty()) {
            throw new InterpreterException(kind == ListValue.Kind.STACK
                    ? "EmptyStackException: pop() on an empty Stack"
                    : "NoSuchElementException: the collection is empty", line);
        }
    }

    // ------------------------------------------------------------------ maps

    private static Outcome map(MapValue map, String method, List<Object> arguments, int line) {
        Map<Object, Object> entries = map.entries();

        switch (method) {
            case "size" -> {
                return Outcome.plain(entries.size());
            }
            case "isEmpty" -> {
                return Outcome.plain(entries.isEmpty());
            }
            case "clear" -> {
                entries.clear();
                return new Outcome(null, -1, true, false);
            }
            case "toString" -> {
                return Outcome.plain(map.render());
            }
            case "put" -> {
                Object previous = entries.put(arguments.get(0), arguments.get(1));
                return Outcome.wrote(previous, map.indexOfKey(arguments.get(0)));
            }
            case "putIfAbsent" -> {
                Object previous = entries.putIfAbsent(arguments.get(0), arguments.get(1));
                return Outcome.wrote(previous, map.indexOfKey(arguments.get(0)));
            }
            case "get" -> {
                int index = map.indexOfKey(arguments.get(0));
                Object value = entries.get(arguments.get(0));
                return index >= 0 ? Outcome.read(value, index) : Outcome.plain(null);
            }
            case "getOrDefault" -> {
                int index = map.indexOfKey(arguments.get(0));
                if (index >= 0) {
                    return Outcome.read(entries.get(arguments.get(0)), index);
                }
                return Outcome.plain(arguments.get(1));
            }
            case "containsKey" -> {
                int index = map.indexOfKey(arguments.get(0));
                return index >= 0 ? Outcome.read(true, index) : Outcome.plain(false);
            }
            case "containsValue" -> {
                return Outcome.plain(entries.containsValue(arguments.get(0)));
            }
            case "remove" -> {
                int index = map.indexOfKey(arguments.get(0));
                Object previous = entries.remove(arguments.get(0));
                return index >= 0 ? Outcome.wrote(previous, index) : Outcome.plain(null);
            }
            case "keySet" -> {
                SetValue keys = new SetValue(map.kind() == MapValue.Kind.TREE_MAP
                        ? SetValue.Kind.TREE_SET : SetValue.Kind.LINKED_HASH_SET);
                keys.elements().addAll(entries.keySet());
                return Outcome.plain(keys);
            }
            case "values" -> {
                ListValue values = new ListValue(ListValue.Kind.ARRAY_LIST);
                values.elements().addAll(entries.values());
                return Outcome.plain(values);
            }
            case "entrySet" -> {
                SetValue set = new SetValue(SetValue.Kind.LINKED_HASH_SET);
                for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                    set.elements().add(new EntryValue(entry.getKey(), entry.getValue()));
                }
                return Outcome.plain(set);
            }
            case "putAll" -> {
                if (arguments.get(0) instanceof MapValue other) {
                    entries.putAll(other.entries());
                    return new Outcome(null, -1, true, false);
                }
                throw new InterpreterException("putAll expects a Map", line);
            }
            case "firstKey" -> {
                requireSortedMap(map, method, line);
                if (entries.isEmpty()) {
                    throw new InterpreterException(
                            "NoSuchElementException: the map is empty", line);
                }
                return Outcome.read(entries.keySet().iterator().next(), 0);
            }
            case "lastKey" -> {
                requireSortedMap(map, method, line);
                if (entries.isEmpty()) {
                    throw new InterpreterException(
                            "NoSuchElementException: the map is empty", line);
                }
                Object last = null;
                for (Object key : entries.keySet()) {
                    last = key;
                }
                return Outcome.read(last, entries.size() - 1);
            }
            case "computeIfAbsent", "merge", "forEach", "compute" ->
                    throw new InterpreterException(
                            "'" + method + "()' needs a lambda, which is not in the supported "
                                    + "subset yet. Use containsKey/getOrDefault with put.",
                            line);
            default -> throw unsupported(map.kind().displayName(), method, line);
        }
    }

    private static void requireSortedMap(MapValue map, String method, int line) {
        if (map.kind() != MapValue.Kind.TREE_MAP) {
            throw new InterpreterException(
                    map.kind().displayName() + " has no '" + method + "()'. Declare a TreeMap.",
                    line);
        }
    }

    // ------------------------------------------------------------------ sets

    private static Outcome set(SetValue set, String method, List<Object> arguments, int line) {
        Set<Object> elements = set.elements();

        switch (method) {
            case "size" -> {
                return Outcome.plain(elements.size());
            }
            case "isEmpty" -> {
                return Outcome.plain(elements.isEmpty());
            }
            case "clear" -> {
                elements.clear();
                return new Outcome(null, -1, true, false);
            }
            case "toString" -> {
                return Outcome.plain(set.render());
            }
            case "add" -> {
                boolean added = elements.add(arguments.get(0));
                return Outcome.wrote(added, set.indexOf(arguments.get(0)));
            }
            case "contains" -> {
                int index = set.indexOf(arguments.get(0));
                return index >= 0 ? Outcome.read(true, index) : Outcome.plain(false);
            }
            case "remove" -> {
                int index = set.indexOf(arguments.get(0));
                boolean removed = elements.remove(arguments.get(0));
                return index >= 0 ? Outcome.wrote(removed, index) : Outcome.plain(false);
            }
            case "addAll" -> {
                for (Object element : iterate(arguments.get(0), line)) {
                    elements.add(element);
                }
                return new Outcome(null, -1, true, false);
            }
            case "first" -> {
                if (elements.isEmpty()) {
                    throw new InterpreterException(
                            "NoSuchElementException: the set is empty", line);
                }
                return Outcome.read(elements.iterator().next(), 0);
            }
            case "iterator", "stream", "forEach" -> throw new InterpreterException(
                    "'" + method + "()' needs lambdas or iterators, which are not in the "
                            + "supported subset yet. Use a for-each loop instead.",
                    line);
            default -> throw unsupported(set.kind().displayName(), method, line);
        }
    }

    // ------------------------------------------------------------------ StringBuilder

    private static Outcome builder(BuilderValue builder, String method, List<Object> arguments,
            int line) {
        StringBuilder content = builder.content();
        try {
            switch (method) {
                case "append" -> {
                    content.append(Values.format(arguments.get(0)));
                    // Returning the builder is what makes chaining work.
                    return Outcome.plain(builder);
                }
                case "toString" -> {
                    return Outcome.plain(content.toString());
                }
                case "length" -> {
                    return Outcome.plain(content.length());
                }
                case "isEmpty" -> {
                    return Outcome.plain(content.length() == 0);
                }
                case "charAt" -> {
                    return Outcome.plain(content.charAt((int) Values.toLong(arguments.get(0))));
                }
                case "reverse" -> {
                    content.reverse();
                    return Outcome.plain(builder);
                }
                case "insert" -> {
                    content.insert((int) Values.toLong(arguments.get(0)),
                            Values.format(arguments.get(1)));
                    return Outcome.plain(builder);
                }
                case "deleteCharAt" -> {
                    content.deleteCharAt((int) Values.toLong(arguments.get(0)));
                    return Outcome.plain(builder);
                }
                case "delete" -> {
                    content.delete((int) Values.toLong(arguments.get(0)),
                            (int) Values.toLong(arguments.get(1)));
                    return Outcome.plain(builder);
                }
                case "setCharAt" -> {
                    content.setCharAt((int) Values.toLong(arguments.get(0)),
                            requireChar(arguments.get(1), line));
                    return Outcome.plain(null);
                }
                case "setLength" -> {
                    content.setLength((int) Values.toLong(arguments.get(0)));
                    return Outcome.plain(null);
                }
                case "indexOf" -> {
                    return Outcome.plain(content.indexOf(Values.format(arguments.get(0))));
                }
                case "substring" -> {
                    return Outcome.plain(arguments.size() == 1
                            ? content.substring((int) Values.toLong(arguments.get(0)))
                            : content.substring((int) Values.toLong(arguments.get(0)),
                                    (int) Values.toLong(arguments.get(1))));
                }
                default -> throw unsupported("StringBuilder", method, line);
            }
        } catch (StringIndexOutOfBoundsException e) {
            throw new InterpreterException(
                    "StringIndexOutOfBoundsException: " + e.getMessage(), line);
        }
    }

    private static char requireChar(Object value, int line) {
        if (value instanceof Character character) {
            return character;
        }
        throw new InterpreterException("Expected a char but got " + Values.typeName(value), line);
    }

    // ------------------------------------------------------------------ statics

    public static Object callStatic(String namespace, String method, List<Object> arguments,
            int line) {
        return switch (namespace) {
            case "Collections" -> collections(method, arguments, line);
            case "List" -> {
                if ("of".equals(method)) {
                    ListValue list = new ListValue(ListValue.Kind.ARRAY_LIST);
                    list.elements().addAll(arguments);
                    yield list;
                }
                if ("copyOf".equals(method)) {
                    ListValue list = new ListValue(ListValue.Kind.ARRAY_LIST);
                    for (Object element : iterate(arguments.get(0), line)) {
                        list.elements().add(element);
                    }
                    yield list;
                }
                throw unsupported("List", method, line);
            }
            case "Objects" -> switch (method) {
                case "equals" -> Values.areEqual(arguments.get(0), arguments.get(1));
                case "isNull" -> arguments.get(0) == null;
                case "nonNull" -> arguments.get(0) != null;
                case "toString" -> Values.format(arguments.get(0));
                default -> throw unsupported("Objects", method, line);
            };
            default -> throw unsupported(namespace, method, line);
        };
    }

    private static Object collections(String method, List<Object> arguments, int line) {
        switch (method) {
            case "sort" -> {
                requireList(arguments.get(0), line).elements().sort(NaturalOrder.INSTANCE);
                return null;
            }
            case "reverse" -> {
                java.util.Collections.reverse(requireList(arguments.get(0), line).elements());
                return null;
            }
            case "swap" -> {
                List<Object> elements = requireList(arguments.get(0), line).elements();
                int a = (int) Values.toLong(arguments.get(1));
                int b = (int) Values.toLong(arguments.get(2));
                checkIndex(a, elements.size(), line, false);
                checkIndex(b, elements.size(), line, false);
                java.util.Collections.swap(elements, a, b);
                return null;
            }
            case "max" -> {
                return extreme(arguments.get(0), line, true);
            }
            case "min" -> {
                return extreme(arguments.get(0), line, false);
            }
            case "emptyList" -> {
                return new ListValue(ListValue.Kind.ARRAY_LIST);
            }
            case "unmodifiableList" -> {
                return requireList(arguments.get(0), line);
            }
            default -> throw unsupported("Collections", method, line);
        }
    }

    private static Object extreme(Object collection, int line, boolean max) {
        Object best = null;
        boolean first = true;
        for (Object element : iterate(collection, line)) {
            if (first || (NaturalOrder.INSTANCE.compare(element, best) > 0) == max) {
                best = element;
                first = false;
            }
        }
        if (first) {
            throw new InterpreterException(
                    "NoSuchElementException: the collection is empty", line);
        }
        return best;
    }

    private static ListValue requireList(Object value, int line) {
        if (value instanceof ListValue list) {
            return list;
        }
        throw new InterpreterException("Expected a List but got " + Values.typeName(value), line);
    }

    // ------------------------------------------------------------------ helpers

    private static int indexOf(List<Object> elements, Object value) {
        for (int i = 0; i < elements.size(); i++) {
            if (Objects.equals(elements.get(i), value)) {
                return i;
            }
        }
        return -1;
    }

    private static int checkIndex(Object raw, int size, int line, boolean allowEnd) {
        int index = (int) Values.toLong(raw);
        int limit = allowEnd ? size : size - 1;
        if (index < 0 || index > limit) {
            throw new InterpreterException(
                    "IndexOutOfBoundsException: Index " + index + " out of bounds for length "
                            + size,
                    line);
        }
        return index;
    }

    /** The order a collection's elements are drawn in, matching its iteration order. */
    public static List<Object> orderedElements(Object container) {
        if (container instanceof ListValue list) {
            return new ArrayList<>(list.elements());
        }
        if (container instanceof SetValue set) {
            return new ArrayList<>(set.elements());
        }
        if (container instanceof MapValue map) {
            List<Object> keys = new ArrayList<>();
            Iterator<Object> iterator = map.entries().keySet().iterator();
            while (iterator.hasNext()) {
                keys.add(iterator.next());
            }
            return keys;
        }
        return List.of();
    }

    private static InterpreterException unsupported(String type, String method, int line) {
        return new InterpreterException(
                type + "." + method + "() is not part of the supported subset yet", line);
    }
}
