package com.algolens.execution.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import com.algolens.entity.ExecutionStatus;
import com.algolens.execution.TestExecutor;
import com.algolens.trace.ExecutionTrace;
import com.algolens.trace.TraceAction;
import com.algolens.trace.TraceEvent;
import com.algolens.trace.VisualizationState;
import com.algolens.trace.VisualizationType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The object model: classes, constructors, {@code this}, reference fields and reference
 * identity. Everything a linked list, a tree or a graph is built out of.
 */
class InterpreterObjectTest {

    /** The submission that motivated this feature, verbatim. */
    private static final String CYCLE_DETECTION = """
            import java.util.*;

            public class Main {
                static class Node {
                    int data;
                    Node next;
                    Node(int data) {
                        this.data = data;
                        this.next = null;
                    }
                }

                public static void main(String[] args) {
                    // Create nodes
                    Node head = new Node(1);
                    head.next = new Node(2);
                    head.next.next = new Node(3);
                    head.next.next.next = new Node(4);

                    // Create a cycle for testing (4 points back to 2)
                    head.next.next.next.next = head.next;

                    // Two-pointer cycle detection logic inside main
                    Node slow = head;
                    Node fast = head;
                    boolean hasCycle = false;

                    while (fast != null && fast.next != null) {
                        slow = slow.next;
                        fast = fast.next.next;

                        if (slow == fast) {
                            hasCycle = true;
                            break;
                        }
                    }

                    // Print result
                    if (hasCycle) {
                        System.out.println("Cycle detected");
                    } else {
                        System.out.println("No cycle detected");
                    }
                }
            }
            """;

    @Test
    @DisplayName("Floyd cycle detection on a cyclic list runs and finds the cycle")
    void cycleDetection() {
        ExecutionTrace trace = TestExecutor.run(CYCLE_DETECTION);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("Cycle detected");
        assertThat(trace.metrics().objectsCreated()).isEqualTo(4);
        assertThat(trace.steps()).isNotEmpty();
    }

    @Test
    @DisplayName("the same algorithm reports no cycle on an acyclic list")
    void noCycle() {
        ExecutionTrace trace = TestExecutor.run(
                CYCLE_DETECTION.replace("head.next.next.next.next = head.next;", ""));

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("No cycle detected");
    }

    @Test
    @DisplayName("a cyclic list is visualized without the serialiser recursing forever")
    void cyclicListIsRenderedSafely() {
        ExecutionTrace trace = TestExecutor.run(CYCLE_DETECTION);

        VisualizationState graph = lastGraph(trace);
        assertThat(graph.type()).isEqualTo(VisualizationType.LINKED_LIST);
        assertThat(graph.nodes()).hasSize(4);
        // The cycle is an edge back to an existing node, not an infinitely unrolled chain.
        assertThat(graph.edges()).hasSize(4);
        assertThat(graph.edges()).allSatisfy(edge -> assertThat(edge.label()).isEqualTo("next"));
        assertThat(graph.edges()).noneMatch(edge -> edge.to() == null);
    }

    @Test
    @DisplayName("node payloads and pointers are exposed for the visualizer")
    void exposesPayloadsAndPointers() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Node {
                        int data;
                        Node next;
                        Node(int data) { this.data = data; }
                    }

                    public static void main(String[] args) {
                        Node head = new Node(10);
                        head.next = new Node(20);
                        Node cursor = head.next;
                    }
                }
                """);

        VisualizationState graph = lastGraph(trace);
        assertThat(graph.nodes()).extracting(VisualizationState.GraphNode::value)
                .containsExactly(10, 20);
        assertThat(graph.nodes()).allSatisfy(
                node -> assertThat(node.className()).isEqualTo("Node"));
        // Both variables pointing into the one list appear as pointers on the one picture.
        assertThat(graph.pointers()).extracting("name").contains("head", "cursor");
        // The tail's null next is an edge to nothing, so the terminator can be drawn.
        assertThat(graph.edges()).anyMatch(edge -> edge.to() == null);
    }

    @Test
    @DisplayName("reference equality compares identity, not contents")
    void referenceEquality() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Node {
                        int data;
                        Node(int data) { this.data = data; }
                    }

                    public static void main(String[] args) {
                        Node a = new Node(1);
                        Node b = new Node(1);
                        Node aliasOfA = a;
                        System.out.println((a == b) + " " + (a == aliasOfA) + " " + (a != b));
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("false true true");
    }

    @Test
    @DisplayName("comparing two references highlights both nodes")
    void referenceComparisonHighlightsBothNodes() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Node {
                        int data;
                        Node next;
                        Node(int data) { this.data = data; }
                    }

                    public static void main(String[] args) {
                        Node a = new Node(1);
                        Node b = a;
                        if (a == b) {
                            System.out.println("same");
                        }
                    }
                }
                """);

        TraceEvent compare = trace.steps().stream()
                .filter(step -> step.action() == TraceAction.COMPARE)
                .findFirst()
                .orElseThrow();

        assertThat(compare.message()).contains("a=Node@1").contains("b=Node@1");
        assertThat(compare.touches()).isNotNull();
        assertThat(compare.touches()).allSatisfy(
                touch -> assertThat(touch.target()).isEqualTo("Node@1"));
    }

    // ------------------------------------------------------------------ list algorithms

    @Test
    @DisplayName("reversing a linked list rewires it correctly")
    void reverseLinkedList() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Node {
                        int data;
                        Node next;
                        Node(int data) { this.data = data; }
                    }

                    public static void main(String[] args) {
                        Node head = new Node(1);
                        head.next = new Node(2);
                        head.next.next = new Node(3);

                        Node previous = null;
                        Node current = head;
                        while (current != null) {
                            Node ahead = current.next;
                            current.next = previous;
                            previous = current;
                            current = ahead;
                        }
                        head = previous;

                        StringBuilderStandIn(head);
                    }

                    static void StringBuilderStandIn(Node node) {
                        while (node != null) {
                            System.out.print(node.data + " ");
                            node = node.next;
                        }
                    }
                }
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("3 2 1");
    }

    @Test
    @DisplayName("finding the middle with slow and fast pointers")
    void findMiddle() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Node {
                        int value;
                        Node next;
                        Node(int value) { this.value = value; }
                    }

                    public static void main(String[] args) {
                        Node head = new Node(1);
                        head.next = new Node(2);
                        head.next.next = new Node(3);
                        head.next.next.next = new Node(4);
                        head.next.next.next.next = new Node(5);

                        Node slow = head;
                        Node fast = head;
                        while (fast != null && fast.next != null) {
                            slow = slow.next;
                            fast = fast.next.next;
                        }
                        System.out.println("Middle: " + slow.value);
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("Middle: 3");
    }

    // ------------------------------------------------------------------ trees

    @Test
    @DisplayName("a BST built with instance methods is drawn as a tree")
    void binarySearchTree() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Node {
                        int key;
                        Node left;
                        Node right;
                        Node(int key) { this.key = key; }
                    }

                    static Node insert(Node root, int key) {
                        if (root == null) {
                            return new Node(key);
                        }
                        if (key < root.key) {
                            root.left = insert(root.left, key);
                        } else if (key > root.key) {
                            root.right = insert(root.right, key);
                        }
                        return root;
                    }

                    static boolean contains(Node root, int key) {
                        while (root != null) {
                            if (key == root.key) {
                                return true;
                            }
                            root = key < root.key ? root.left : root.right;
                        }
                        return false;
                    }

                    public static void main(String[] args) {
                        Node root = null;
                        root = insert(root, 50);
                        insert(root, 30);
                        insert(root, 70);
                        insert(root, 20);
                        System.out.println(contains(root, 20) + " " + contains(root, 99));
                    }
                }
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("true false");
        assertThat(trace.metrics().objectsCreated()).isEqualTo(4);

        VisualizationState graph = lastGraph(trace);
        assertThat(graph.type()).isEqualTo(VisualizationType.TREE);
        assertThat(graph.nodes()).hasSize(4);
    }

    @Test
    @DisplayName("an object with two unrecognised reference fields is drawn as a graph")
    void generalObjectGraph() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class City {
                        String name;
                        City north;
                        City east;
                        City(String name) { this.name = name; }
                    }

                    public static void main(String[] args) {
                        City home = new City("Home");
                        home.north = new City("North");
                        home.east = new City("East");
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        VisualizationState graph = lastGraph(trace);
        assertThat(graph.type()).isEqualTo(VisualizationType.GRAPH);
        assertThat(graph.nodes()).extracting(VisualizationState.GraphNode::value)
                .contains("Home", "North", "East");
    }

    // ------------------------------------------------------------------ classes & methods

    @Test
    @DisplayName("instance methods bind 'this' and can be called on an object")
    void instanceMethods() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Counter {
                        int count;

                        void increment() {
                            count = count + 1;
                        }

                        int doubled() {
                            return this.count * 2;
                        }
                    }

                    public static void main(String[] args) {
                        Counter counter = new Counter();
                        counter.increment();
                        counter.increment();
                        counter.increment();
                        System.out.println(counter.count + " " + counter.doubled());
                    }
                }
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("3 6");
    }

    @Test
    @DisplayName("constructors and methods overload on argument count")
    void arityOverloading() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Point {
                        int x;
                        int y;
                        Point() { this.x = 0; this.y = 0; }
                        Point(int x, int y) { this.x = x; this.y = y; }
                    }

                    static int sum(int a) { return a; }
                    static int sum(int a, int b) { return a + b; }
                    static int sum(int a, int b, int c) { return a + b + c; }

                    public static void main(String[] args) {
                        Point origin = new Point();
                        Point other = new Point(3, 4);
                        System.out.println(origin.x + " " + other.x + " " + other.y
                            + " " + sum(1) + " " + sum(1, 2) + " " + sum(1, 2, 3));
                    }
                }
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("0 3 4 1 3 6");
    }

    @Test
    @DisplayName("field initialisers run before the constructor")
    void fieldInitialisers() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Box {
                        int size = 7;
                        boolean open = true;
                        int doubled = 0;

                        Box() {
                            this.doubled = this.size * 2;
                        }
                    }

                    public static void main(String[] args) {
                        Box box = new Box();
                        System.out.println(box.size + " " + box.open + " " + box.doubled);
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("7 true 14");
    }

    @Test
    @DisplayName("several top-level classes work, with main in any of them")
    void multipleTopLevelClasses() {
        ExecutionTrace trace = TestExecutor.run("""
                class Node {
                    int data;
                    Node next;
                    Node(int data) { this.data = data; }
                }

                public class Main {
                    public static void main(String[] args) {
                        Node head = new Node(42);
                        System.out.println(head.data);
                    }
                }
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("42");
    }

    @Test
    @DisplayName("a class plus bare statements needs no main")
    void classPlusBareStatements() {
        ExecutionTrace trace = TestExecutor.run("""
                class Node {
                    int data;
                    Node next;
                    Node(int data) { this.data = data; }
                }

                Node head = new Node(1);
                head.next = new Node(2);
                System.out.println(head.data + " -> " + head.next.data);
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("1 -> 2");
    }

    @Test
    @DisplayName("an array of objects works and its elements are reachable for drawing")
    void arrayOfObjects() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Node {
                        int data;
                        Node next;
                        Node(int data) { this.data = data; }
                    }

                    public static void main(String[] args) {
                        Node[] buckets = new Node[3];
                        buckets[0] = new Node(5);
                        buckets[2] = new Node(9);
                        System.out.println(buckets[0].data + " " + buckets[2].data);
                    }
                }
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("5 9");
        // The array itself is drawn, and so are the objects it holds.
        List<VisualizationState> visualizations = lastVisualizations(trace);
        assertThat(visualizations).anyMatch(v -> v.type() == VisualizationType.ARRAY);
        assertThat(visualizations).anyMatch(v -> v.nodes() != null);
    }

    // ------------------------------------------------------------------ failure paths

    @Test
    @DisplayName("dereferencing null names the expression that was null")
    void nullDereference() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Node {
                        int data;
                        Node next;
                        Node(int data) { this.data = data; }
                    }

                    public static void main(String[] args) {
                        Node head = new Node(1);
                        System.out.println(head.next.data);
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("NullPointerException")
                .contains("head.next");
        assertThat(trace.steps()).isNotEmpty();
    }

    @Test
    @DisplayName("an unknown field names the class it is missing from")
    void unknownField() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class Node {
                        int data;
                        Node(int data) { this.data = data; }
                    }

                    public static void main(String[] args) {
                        Node head = new Node(1);
                        System.out.println(head.missing);
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("Node has no field 'missing'");
    }

    @Test
    @DisplayName("an undeclared class is reported, listing the library types that do exist")
    void unknownClass() {
        ExecutionTrace trace = TestExecutor.run("""
                Widget thing = new Widget();
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage())
                .contains("Widget")
                .contains("ArrayList");
    }

    @Test
    @DisplayName("assigning the wrong class to a reference is rejected")
    void wrongTypeAssignment() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static class A { int x; }
                    static class B { int y; }

                    public static void main(String[] args) {
                        A a = new B();
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("B").contains("A");
    }

    @Test
    @DisplayName("two same-arity overloads are refused rather than mis-dispatched")
    void ambiguousOverloadsAreRefused() {
        ExecutionTrace trace = TestExecutor.run("""
                static int f(int a) { return a; }
                static int f(double a) { return 1; }

                int x = f(2);
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
        assertThat(trace.errorMessage()).contains("argument count");
    }

    @Test
    @DisplayName("a wrong argument count names the arities that exist")
    void wrongArgumentCount() {
        ExecutionTrace trace = TestExecutor.run("""
                static int twice(int a) { return a * 2; }

                int x = twice(1, 2);
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("twice").contains("1");
    }

    @Test
    @DisplayName("'this' outside an instance method is reported clearly")
    void thisInStaticContext() {
        ExecutionTrace trace = TestExecutor.run("""
                int x = 1;
                System.out.println(this);
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("'this'");
    }

    @Test
    @DisplayName("building an unbounded list is stopped by the step budget")
    void unboundedAllocationIsStopped() {
        ExecutionTrace trace = TestExecutor.runWithLimits("""
                public class Main {
                    static class Node {
                        int data;
                        Node next;
                        Node(int data) { this.data = data; }
                    }

                    public static void main(String[] args) {
                        Node head = new Node(0);
                        Node cursor = head;
                        while (true) {
                            cursor.next = new Node(1);
                            cursor = cursor.next;
                        }
                    }
                }
                """, 400, 100_000, 10_000);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.LIMIT_EXCEEDED);
        assertThat(trace.truncated()).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The last object graph anywhere in the trace.
     *
     * <p>Searches for a step that actually contains a graph rather than taking the last step
     * that has any visualization at all -- a step can legitimately show only an array while the
     * objects are out of scope, and treating that as "no graph" made this helper lie.
     */
    private static VisualizationState lastGraph(ExecutionTrace trace) {
        for (int i = trace.steps().size() - 1; i >= 0; i--) {
            for (VisualizationState visualization : visualizationsAt(trace, i)) {
                if (visualization.nodes() != null) {
                    return visualization;
                }
            }
        }
        throw new AssertionError("No object graph in the trace");
    }

    private static List<VisualizationState> lastVisualizations(ExecutionTrace trace) {
        for (int i = trace.steps().size() - 1; i >= 0; i--) {
            List<VisualizationState> visualizations = visualizationsAt(trace, i);
            if (!visualizations.isEmpty()) {
                return visualizations;
            }
        }
        throw new AssertionError("No visualizations in the trace");
    }

    private static List<VisualizationState> visualizationsAt(ExecutionTrace trace, int step) {
        List<VisualizationState> visualizations = trace.steps().get(step).visualizations();
        return visualizations == null ? List.of() : visualizations;
    }
}
