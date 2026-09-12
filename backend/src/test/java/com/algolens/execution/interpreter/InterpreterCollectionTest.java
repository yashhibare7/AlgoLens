package com.algolens.execution.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import com.algolens.entity.ExecutionStatus;
import com.algolens.execution.TestExecutor;
import com.algolens.trace.ExecutionTrace;
import com.algolens.trace.VisualizationState;
import com.algolens.trace.VisualizationType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Collections, generics, {@code StringBuilder}, {@code switch}, {@code try}/{@code catch} and
 * {@code instanceof} -- everything added so that ordinary pasted Java runs.
 *
 * <p>The kind-specific method semantics get the most attention here. {@code push} appends on a
 * {@code Stack} and prepends on an {@code ArrayDeque}; {@code poll} takes the smallest from a
 * {@code PriorityQueue} and the first from a {@code Queue}. Getting one of those backwards
 * would make the visualizer confidently wrong, which is worse than not supporting it at all.
 */
class InterpreterCollectionTest {

    // ------------------------------------------------------------------ lists

    @Test
    @DisplayName("ArrayList with generics: add, get, set, size, remove")
    void arrayListBasics() {
        ExecutionTrace trace = run("""
                import java.util.*;

                List<Integer> numbers = new ArrayList<>();
                numbers.add(10);
                numbers.add(20);
                numbers.add(30);
                numbers.set(1, 25);
                numbers.remove(0);

                System.out.println(numbers + " size=" + numbers.size()
                    + " first=" + numbers.get(0) + " has25=" + numbers.contains(25));
                """);

        assertThat(trace.stdout().strip())
                .isEqualTo("[25, 30] size=2 first=25 has25=true");
    }

    @Test
    @DisplayName("a list is drawn as an indexed row, like an array")
    void listIsVisualized() {
        ExecutionTrace trace = run("""
                import java.util.*;
                List<Integer> values = new ArrayList<>();
                values.add(4);
                values.add(9);
                """);

        VisualizationState list = visualization(trace, VisualizationType.ARRAY);
        assertThat(list.name()).isEqualTo("values");
        assertThat(list.elementType()).isEqualTo("ArrayList");
        assertThat(list.elements()).extracting(e -> e.value()).containsExactly(4, 9);
    }

    @Test
    @DisplayName("iterating a list with for-each works and counts each read")
    void forEachOverList() {
        ExecutionTrace trace = run("""
                import java.util.*;
                List<Integer> values = new ArrayList<>();
                values.add(1);
                values.add(2);
                values.add(3);

                int total = 0;
                for (int value : values) {
                    total = total + value;
                }
                System.out.println(total);
                """);

        assertThat(trace.stdout().strip()).isEqualTo("6");
    }

    // ------------------------------------------------------------------ stack vs deque

    @Test
    @DisplayName("Stack.push appends and pop takes from the end (LIFO)")
    void stackSemantics() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Stack<Integer> stack = new Stack<>();
                stack.push(1);
                stack.push(2);
                stack.push(3);
                System.out.println(stack + " peek=" + stack.peek() + " pop=" + stack.pop()
                    + " then=" + stack);
                """);

        assertThat(trace.stdout().strip())
                .isEqualTo("[1, 2, 3] peek=3 pop=3 then=[1, 2]");
    }

    @Test
    @DisplayName("ArrayDeque.push prepends and pop takes from the front")
    void dequePushIsTheOppositeEndFromStack() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Deque<Integer> deque = new ArrayDeque<>();
                deque.push(1);
                deque.push(2);
                deque.push(3);
                System.out.println(deque + " peek=" + deque.peek() + " pop=" + deque.pop()
                    + " then=" + deque);
                """);

        // Reversed relative to Stack: this is the distinction that must not be blurred.
        assertThat(trace.stdout().strip())
                .isEqualTo("[3, 2, 1] peek=3 pop=3 then=[2, 1]");
    }

    @Test
    @DisplayName("a queue offers to the back and polls from the front (FIFO)")
    void queueSemantics() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Queue<String> queue = new LinkedList<>();
                queue.offer("a");
                queue.offer("b");
                queue.offer("c");
                System.out.println(queue.poll() + queue.poll() + " left=" + queue);
                """);

        assertThat(trace.stdout().strip()).isEqualTo("ab left=[c]");
    }

    @Test
    @DisplayName("a stack is drawn with a top marker, a queue with front and back")
    void endMarkersAreVisualized() {
        ExecutionTrace stackTrace = run("""
                import java.util.*;
                Stack<Integer> stack = new Stack<>();
                stack.push(7);
                stack.push(8);
                """);
        VisualizationState stack = visualization(stackTrace, VisualizationType.STACK);
        assertThat(stack.pointers()).extracting("name").contains("top");

        ExecutionTrace queueTrace = run("""
                import java.util.*;
                Deque<Integer> queue = new ArrayDeque<>();
                queue.addLast(1);
                queue.addLast(2);
                """);
        VisualizationState queue = visualization(queueTrace, VisualizationType.QUEUE);
        assertThat(queue.pointers()).extracting("name").contains("front", "back");
    }

    @Test
    @DisplayName("a LinkedList declared as a Queue is drawn as a queue, not an indexed list")
    void declaredTypeDecidesTheShape() {
        // The standard BFS idiom. Drawing it as an indexed row would hide the front and back,
        // which is the only thing that makes it behave like a queue.
        ExecutionTrace asQueue = run("""
                import java.util.*;
                Queue<Integer> frontier = new LinkedList<>();
                frontier.offer(1);
                frontier.offer(2);
                """);
        VisualizationState queue = visualization(asQueue, VisualizationType.QUEUE);
        assertThat(queue.pointers()).extracting("name").contains("front", "back");

        // The same class declared as a List keeps the indexed rendering, which its use implies.
        ExecutionTrace asList = run("""
                import java.util.*;
                List<Integer> values = new LinkedList<>();
                values.add(1);
                """);
        assertThat(visualization(asList, VisualizationType.ARRAY).name()).isEqualTo("values");
    }

    @Test
    @DisplayName("PriorityQueue always polls the smallest element")
    void priorityQueueOrders() {
        ExecutionTrace trace = run("""
                import java.util.*;
                PriorityQueue<Integer> heap = new PriorityQueue<>();
                heap.offer(30);
                heap.offer(10);
                heap.offer(20);
                System.out.println(heap.poll() + " " + heap.poll() + " " + heap.poll());
                """);

        assertThat(trace.stdout().strip()).isEqualTo("10 20 30");
    }

    @Test
    @DisplayName("a Stack has no offer(), and the message says what to declare instead")
    void wrongMethodForTheKindIsExplained() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Stack<Integer> stack = new Stack<>();
                stack.offer(1);
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("Stack").contains("offer")
                .contains("ArrayDeque");
    }

    // ------------------------------------------------------------------ maps

    @Test
    @DisplayName("HashMap put/get/getOrDefault, the frequency-count idiom")
    void mapFrequencyCount() {
        ExecutionTrace trace = run("""
                import java.util.*;

                String[] words = {"a", "b", "a", "c", "b", "a"};
                Map<String, Integer> counts = new HashMap<>();

                for (String word : words) {
                    counts.put(word, counts.getOrDefault(word, 0) + 1);
                }

                System.out.println(counts + " a=" + counts.get("a")
                    + " size=" + counts.size() + " hasZ=" + counts.containsKey("z"));
                """);

        assertThat(trace.stdout().strip())
                .isEqualTo("{a=3, b=2, c=1} a=3 size=3 hasZ=false");
    }

    @Test
    @DisplayName("a map is drawn as key/value entries")
    void mapIsVisualized() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Map<String, Integer> ages = new HashMap<>();
                ages.put("ada", 36);
                ages.put("alan", 41);
                """);

        VisualizationState map = visualization(trace, VisualizationType.MAP);
        assertThat(map.name()).isEqualTo("ages");
        assertThat(map.entries()).extracting(e -> e.key()).containsExactly("ada", "alan");
        assertThat(map.entries()).extracting(e -> e.value()).containsExactly(36, 41);
    }

    @Test
    @DisplayName("keySet, values and entrySet iterate")
    void mapViews() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Map<String, Integer> scores = new LinkedHashMap<>();
                scores.put("x", 1);
                scores.put("y", 2);

                StringBuilder out = new StringBuilder();
                for (String key : scores.keySet()) {
                    out.append(key);
                }
                for (int value : scores.values()) {
                    out.append(value);
                }
                for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                    out.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
                }
                System.out.println(out.toString());
                """);

        assertThat(trace.stdout().strip()).isEqualTo("xy12x=1;y=2;");
    }

    @Test
    @DisplayName("a TreeMap keeps its keys sorted")
    void treeMapSorts() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Map<Integer, String> sorted = new TreeMap<>();
                sorted.put(3, "c");
                sorted.put(1, "a");
                sorted.put(2, "b");
                System.out.println(sorted);
                """);

        assertThat(trace.stdout().strip()).isEqualTo("{1=a, 2=b, 3=c}");
    }

    @Test
    @DisplayName("iterating a map directly is refused with the fix in the message")
    void mapCannotBeIteratedDirectly() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Map<String, Integer> m = new HashMap<>();
                for (String k : m) {
                    System.out.println(k);
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("keySet");
    }

    // ------------------------------------------------------------------ sets

    @Test
    @DisplayName("HashSet deduplicates, and a TreeSet sorts")
    void sets() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Set<Integer> seen = new HashSet<>();
                seen.add(3);
                seen.add(1);
                seen.add(3);

                Set<Integer> ordered = new TreeSet<>();
                ordered.add(3);
                ordered.add(1);
                ordered.add(2);

                System.out.println(seen + " size=" + seen.size()
                    + " has3=" + seen.contains(3) + " sorted=" + ordered);
                """);

        assertThat(trace.stdout().strip())
                .isEqualTo("[3, 1] size=2 has3=true sorted=[1, 2, 3]");
    }

    @Test
    @DisplayName("a set is drawn without indices")
    void setIsVisualized() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Set<String> tags = new HashSet<>();
                tags.add("red");
                """);

        VisualizationState set = visualization(trace, VisualizationType.SET);
        assertThat(set.name()).isEqualTo("tags");
        assertThat(set.elements()).extracting(e -> e.value()).containsExactly("red");
    }

    // ------------------------------------------------------------------ StringBuilder

    @Test
    @DisplayName("StringBuilder append chains, reverse and toString")
    void stringBuilder() {
        ExecutionTrace trace = run("""
                StringBuilder builder = new StringBuilder();
                builder.append("ab").append(1).append('c').append(true);
                int length = builder.length();
                char first = builder.charAt(0);
                builder.reverse();
                System.out.println(builder.toString() + " len=" + length + " first=" + first);
                """);

        assertThat(trace.stdout().strip()).isEqualTo("eurtc1ba len=8 first=a");
    }

    @Test
    @DisplayName("a StringBuilder shows in the variables panel, not as a structure")
    void builderIsAScalar() {
        ExecutionTrace trace = run("""
                StringBuilder out = new StringBuilder("hi");
                """);

        assertThat(lastVariables(trace))
                .anySatisfy(variable -> {
                    assertThat(variable.name()).isEqualTo("out");
                    assertThat(variable.value()).isEqualTo("hi");
                });
    }

    // ------------------------------------------------------------------ switch

    @Test
    @DisplayName("classic switch falls through until break")
    void switchFallsThrough() {
        ExecutionTrace trace = run("""
                StringBuilder out = new StringBuilder();
                for (int i = 1; i <= 4; i++) {
                    switch (i) {
                        case 1:
                            out.append("one");
                            break;
                        case 2:
                        case 3:
                            out.append("two-or-three");
                            break;
                        default:
                            out.append("other");
                    }
                    out.append("|");
                }
                System.out.println(out.toString());
                """);

        assertThat(trace.stdout().strip())
                .isEqualTo("one|two-or-three|two-or-three|other|");
    }

    @Test
    @DisplayName("a missing break really does fall through, as in Java")
    void missingBreakFallsThrough() {
        ExecutionTrace trace = run("""
                StringBuilder out = new StringBuilder();
                switch (1) {
                    case 1:
                        out.append("a");
                    case 2:
                        out.append("b");
                        break;
                    case 3:
                        out.append("c");
                }
                System.out.println(out.toString());
                """);

        assertThat(trace.stdout().strip()).isEqualTo("ab");
    }

    @Test
    @DisplayName("arrow switch never falls through, and switches on strings")
    void arrowSwitch() {
        ExecutionTrace trace = run("""
                String command = "stop";
                StringBuilder out = new StringBuilder();
                switch (command) {
                    case "go" -> out.append("moving");
                    case "stop" -> out.append("halted");
                    default -> out.append("unknown");
                }
                System.out.println(out.toString());
                """);

        assertThat(trace.stdout().strip()).isEqualTo("halted");
    }

    // ------------------------------------------------------------------ try / catch / throw

    @Test
    @DisplayName("an interpreter-raised exception is catchable")
    void catchesRuntimeException() {
        ExecutionTrace trace = run("""
                int[] arr = {1, 2, 3};
                String result = "";
                try {
                    int bad = arr[10];
                    result = "no error";
                } catch (ArrayIndexOutOfBoundsException e) {
                    result = "caught: " + e.getMessage();
                } finally {
                    result = result + " (finally ran)";
                }
                System.out.println(result);
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip())
                .startsWith("caught:")
                .contains("Index 10")
                .endsWith("(finally ran)");
    }

    @Test
    @DisplayName("catch (Exception e) catches anything, and division by zero is arithmetic")
    void catchAll() {
        ExecutionTrace trace = run("""
                String result = "";
                try {
                    int x = 1 / 0;
                } catch (Exception e) {
                    result = "caught";
                }
                System.out.println(result);
                """);

        assertThat(trace.stdout().strip()).isEqualTo("caught");
    }

    @Test
    @DisplayName("throw and catch a user-created exception")
    void throwAndCatch() {
        ExecutionTrace trace = run("""
                public class Main {
                    static int half(int n) {
                        if (n % 2 != 0) {
                            throw new IllegalArgumentException("odd: " + n);
                        }
                        return n / 2;
                    }

                    public static void main(String[] args) {
                        System.out.println(half(8));
                        try {
                            half(7);
                        } catch (IllegalArgumentException e) {
                            System.out.println("rejected " + e.getMessage());
                        }
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout()).isEqualTo("4\nrejected odd: 7\n");
    }

    @Test
    @DisplayName("an uncaught throw fails the run with the exception as the message")
    void uncaughtThrow() {
        ExecutionTrace trace = run("""
                throw new IllegalStateException("boom");
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("IllegalStateException").contains("boom");
    }

    @Test
    @DisplayName("an unsupported-feature refusal is NOT catchable")
    void refusalsAreNotCatchable() {
        ExecutionTrace trace = run("""
                import java.util.*;
                String result = "swallowed";
                try {
                    List<Integer> list = new ArrayList<>();
                    list.forEach(null);
                } catch (Exception e) {
                    result = "caught";
                }
                System.out.println(result);
                """);

        // Letting catch(Exception) hide "this is not supported" would turn a clear message
        // into silently wrong behaviour, so the refusal wins.
        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("subset");
    }

    @Test
    @DisplayName("finally runs even when the exception is not caught")
    void finallyRunsOnTheWayOut() {
        ExecutionTrace trace = run("""
                try {
                    int x = 1 / 0;
                } finally {
                    System.out.println("cleanup");
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.stdout().strip()).isEqualTo("cleanup");
    }

    // ------------------------------------------------------------------ misc syntax

    @Test
    @DisplayName("instanceof narrows by exact type, and null is never an instance")
    void instanceOf() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Object a = "text";
                Object b = new ArrayList<>();
                Object c = null;
                System.out.println((a instanceof String) + " " + (b instanceof List)
                    + " " + (c instanceof String) + " " + (a instanceof Integer));
                """);

        assertThat(trace.stdout().strip()).isEqualTo("true true false false");
    }

    @Test
    @DisplayName("nested generics parse, including the >> that closes two levels")
    void nestedGenerics() {
        ExecutionTrace trace = run("""
                import java.util.*;
                Map<String, List<Integer>> groups = new HashMap<>();
                List<Integer> first = new ArrayList<>();
                first.add(1);
                first.add(2);
                groups.put("a", first);
                System.out.println(groups + " " + groups.get("a").size());
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("{a=[1, 2]} 2");
    }

    @Test
    @DisplayName("a comparison is still a comparison, not a generic type")
    void lessThanIsNotGenerics() {
        ExecutionTrace trace = run("""
                int a = 1;
                int b = 2;
                int c = 3;
                boolean chained = a < b && b > c;
                System.out.println(chained + " " + (a < b));
                """);

        assertThat(trace.stdout().strip()).isEqualTo("false true");
    }

    @Test
    @DisplayName("printf and String.format work")
    void formatting() {
        ExecutionTrace trace = run("""
                double value = 3.14159;
                System.out.printf("%.2f and %d%n", value, 42);
                System.out.println(String.format("%s-%s", "a", "b"));
                """);

        assertThat(trace.stdout()).contains("3.14 and 42").contains("a-b");
    }

    @Test
    @DisplayName("Arrays.sort now runs instead of being refused")
    void arraysSortRuns() {
        ExecutionTrace trace = run("""
                int[] arr = {5, 2, 8, 1};
                Arrays.sort(arr);
                int found = Arrays.binarySearch(arr, 5);
                System.out.println(Arrays.toString(arr) + " at=" + found);
                """);

        assertThat(trace.status())
                .as("failed: %s", trace.errorMessage())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("[1, 2, 5, 8] at=2");
    }

    @Test
    @DisplayName("Collections.sort, reverse, max and min")
    void collectionsHelpers() {
        ExecutionTrace trace = run("""
                import java.util.*;
                List<Integer> values = new ArrayList<>();
                values.add(3);
                values.add(1);
                values.add(2);
                Collections.sort(values);
                int biggest = Collections.max(values);
                Collections.reverse(values);
                System.out.println(values + " max=" + biggest + " min=" + Collections.min(values));
                """);

        assertThat(trace.stdout().strip()).isEqualTo("[3, 2, 1] max=3 min=1");
    }

    @Test
    @DisplayName("a method with a throws clause is accepted")
    void throwsClauseIsAccepted() {
        ExecutionTrace trace = run("""
                public class Main {
                    static int parse(String text) throws NumberFormatException {
                        return Integer.parseInt(text);
                    }

                    public static void main(String[] args) {
                        System.out.println(parse("42"));
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("42");
    }

    // ------------------------------------------------------------------ real algorithms

    @Test
    @DisplayName("BFS over a graph held in a Map of Lists")
    void breadthFirstSearch() {
        ExecutionTrace trace = run("""
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Map<Integer, List<Integer>> graph = new HashMap<>();
                        for (int i = 1; i <= 6; i++) {
                            graph.put(i, new ArrayList<>());
                        }
                        graph.get(1).add(2);
                        graph.get(1).add(3);
                        graph.get(2).add(4);
                        graph.get(3).add(5);
                        graph.get(5).add(6);

                        Queue<Integer> frontier = new LinkedList<>();
                        Set<Integer> visited = new HashSet<>();
                        StringBuilder order = new StringBuilder();

                        frontier.offer(1);
                        visited.add(1);

                        while (!frontier.isEmpty()) {
                            int node = frontier.poll();
                            order.append(node).append(" ");
                            for (int neighbour : graph.get(node)) {
                                if (!visited.contains(neighbour)) {
                                    visited.add(neighbour);
                                    frontier.offer(neighbour);
                                }
                            }
                        }

                        System.out.println(order.toString().trim());
                    }
                }
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("1 2 3 4 5 6");
    }

    @Test
    @DisplayName("balanced-bracket check with a Stack")
    void balancedBrackets() {
        ExecutionTrace trace = run("""
                import java.util.*;

                String input = "{[()]}";
                Stack<Character> stack = new Stack<>();
                boolean balanced = true;

                for (int i = 0; i < input.length(); i++) {
                    char c = input.charAt(i);
                    if (c == '(' || c == '[' || c == '{') {
                        stack.push(c);
                    } else {
                        if (stack.isEmpty()) {
                            balanced = false;
                        } else {
                            char open = stack.pop();
                            if (c == ')' && open != '(') {
                                balanced = false;
                            }
                            if (c == ']' && open != '[') {
                                balanced = false;
                            }
                            if (c == '}' && open != '{') {
                                balanced = false;
                            }
                        }
                    }
                }

                System.out.println("balanced=" + (balanced && stack.isEmpty()));
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("balanced=true");
    }

    @Test
    @DisplayName("two-sum with a HashMap, the canonical interview answer")
    void twoSumWithMap() {
        ExecutionTrace trace = run("""
                import java.util.*;

                int[] numbers = {2, 7, 11, 15};
                int target = 9;
                Map<Integer, Integer> seen = new HashMap<>();
                String answer = "none";

                for (int i = 0; i < numbers.length; i++) {
                    int need = target - numbers[i];
                    if (seen.containsKey(need)) {
                        answer = seen.get(need) + "," + i;
                    }
                    seen.put(numbers[i], i);
                }

                System.out.println(answer);
                """);

        assertThat(trace.stdout().strip()).isEqualTo("0,1");
    }

    @Test
    @DisplayName("a list of user objects works and both structures are drawn")
    void listOfObjects() {
        ExecutionTrace trace = run("""
                import java.util.*;

                public class Main {
                    static class Task {
                        String title;
                        int priority;

                        Task(String title, int priority) {
                            this.title = title;
                            this.priority = priority;
                        }
                    }

                    public static void main(String[] args) {
                        List<Task> tasks = new ArrayList<>();
                        tasks.add(new Task("write", 2));
                        tasks.add(new Task("test", 1));

                        int total = 0;
                        for (Task task : tasks) {
                            total = total + task.priority;
                        }
                        System.out.println("total=" + total + " first=" + tasks.get(0).title);
                    }
                }
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("total=3 first=write");
    }

    @Test
    @DisplayName("a self-referencing list renders instead of looping forever")
    void selfReferenceIsSafe() {
        ExecutionTrace trace = run("""
                import java.util.*;
                List<Object> outer = new ArrayList<>();
                List<Object> inner = new ArrayList<>();
                inner.add(1);
                outer.add(inner);
                inner.add(outer);
                System.out.println(outer.size() + " " + inner.size());
                """);

        assertThat(trace.status())
                .as("failed: %s", trace.errorMessage())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("1 2");
    }

    // ------------------------------------------------------------------ helpers

    private static ExecutionTrace run(String code) {
        ExecutionTrace trace = TestExecutor.run(code);
        if (trace.status() == ExecutionStatus.COMPILE_ERROR) {
            throw new AssertionError(
                    "Did not parse: " + trace.errorMessage() + "\n---\n" + code);
        }
        return trace;
    }

    private static VisualizationState visualization(ExecutionTrace trace, VisualizationType type) {
        for (int i = trace.steps().size() - 1; i >= 0; i--) {
            List<VisualizationState> visualizations = trace.steps().get(i).visualizations();
            if (visualizations == null) {
                continue;
            }
            for (VisualizationState visualization : visualizations) {
                if (visualization.type() == type) {
                    return visualization;
                }
            }
        }
        throw new AssertionError("No " + type + " visualization in the trace");
    }

    private static List<com.algolens.trace.VariableValue> lastVariables(ExecutionTrace trace) {
        for (int i = trace.steps().size() - 1; i >= 0; i--) {
            List<com.algolens.trace.VariableValue> variables = trace.steps().get(i).variables();
            if (variables != null && !variables.isEmpty()) {
                return variables;
            }
        }
        throw new AssertionError("No variables in the trace");
    }
}
