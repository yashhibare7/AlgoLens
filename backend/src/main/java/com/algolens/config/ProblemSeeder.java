package com.algolens.config;

import com.algolens.entity.Difficulty;
import com.algolens.entity.Language;
import com.algolens.entity.Problem;
import com.algolens.repository.ProblemRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the starter problem library on first boot.
 *
 * <p>Kept in Java rather than in a Flyway migration or a JSON resource on purpose: the starter
 * code is multi-line Java, and embedding it in SQL string literals or JSON means escaping every
 * quote and newline -- which is exactly how seed data quietly stops compiling as valid input to
 * the very interpreter it is meant to demonstrate. Text blocks keep it readable and reviewable.
 *
 * <p>Every snippet here is inside the supported subset and is used as a smoke test in
 * {@code ProblemSeedCompilesTest}, so a change to the parser that breaks the library fails the
 * build rather than the demo.
 */
@Component
public class ProblemSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProblemSeeder.class);

    private final ProblemRepository problems;

    public ProblemSeeder(ProblemRepository problems) {
        this.problems = problems;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (problems.count() > 0) {
            return;
        }
        List<Problem> seeded = problems.saveAll(library());
        log.info("Seeded {} starter problems", seeded.size());
    }

    /** The starter library. Order here is the order shown in the UI. */
    public static List<Problem> library() {
        List<Problem> library = new ArrayList<>();
        int order = 0;

        library.add(new Problem("sum-of-array", "Sum of an Array",
                """
                Add up every element of an array and print the total and the average.

                A gentle first run: watch the `sum` variable grow in the variables panel while
                the highlighted cell walks left to right through the array.
                """,
                Difficulty.EASY, "Arrays", Language.JAVA,
                """
                int[] numbers = {4, 8, 15, 16, 23, 42};
                int sum = 0;

                for (int value : numbers) {
                    sum = sum + value;
                }

                System.out.println("Sum: " + sum);
                System.out.println("Average: " + (sum / numbers.length));
                """,
                null, order++));

        library.add(new Problem("find-max", "Find the Largest Element",
                """
                Scan an array once and find its largest value.

                Notice there is exactly one comparison per element: that is what makes this O(n),
                and the comparison counter next to the timeline proves it.
                """,
                Difficulty.EASY, "Arrays", Language.JAVA,
                """
                int[] arr = {3, 41, 12, 9, 74, 20};
                int largest = arr[0];

                for (int i = 1; i < arr.length; i++) {
                    if (arr[i] > largest) {
                        largest = arr[i];
                    }
                }

                System.out.println("Largest: " + largest);
                """,
                null, order++));

        library.add(new Problem("linear-search", "Linear Search",
                """
                Find the index of a target value by checking each element in turn.

                Step through and watch it stop the moment it finds the target -- the `break` is
                why the average case is n/2 comparisons rather than n.
                """,
                Difficulty.EASY, "Searching", Language.JAVA,
                """
                int[] arr = {4, 8, 15, 16, 23, 42};
                int target = 16;
                int foundAt = -1;

                for (int i = 0; i < arr.length; i++) {
                    if (arr[i] == target) {
                        foundAt = i;
                        break;
                    }
                }

                System.out.println("Found at index: " + foundAt);
                """,
                null, order++));

        library.add(new Problem("count-occurrences", "Count Occurrences",
                """
                Count how many times a value appears in an array.

                Same single pass as linear search, but without the early exit -- compare the
                comparison counts of the two runs.
                """,
                Difficulty.EASY, "Arrays", Language.JAVA,
                """
                int[] arr = {3, 7, 3, 1, 3, 9, 7};
                int target = 3;
                int count = 0;

                for (int i = 0; i < arr.length; i++) {
                    if (arr[i] == target) {
                        count++;
                    }
                }

                System.out.println(target + " appears " + count + " time(s)");
                """,
                null, order++));

        library.add(new Problem("binary-search", "Binary Search",
                """
                Find a target in a **sorted** array by halving the search range each step.

                The `low`, `high` and `mid` pointers are drawn under the array. Watch how the
                range collapses: ten elements are searched in at most four comparisons, which is
                what O(log n) looks like on screen.
                """,
                Difficulty.MEDIUM, "Searching", Language.JAVA,
                """
                int[] arr = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
                int target = 23;

                int low = 0;
                int high = arr.length - 1;
                int foundAt = -1;

                while (low <= high) {
                    int mid = low + (high - low) / 2;

                    if (arr[mid] == target) {
                        foundAt = mid;
                        break;
                    } else if (arr[mid] < target) {
                        low = mid + 1;
                    } else {
                        high = mid - 1;
                    }
                }

                System.out.println("Found at index: " + foundAt);
                """,
                null, order++));

        library.add(new Problem("reverse-array", "Reverse an Array in Place",
                """
                Reverse an array using two pointers that walk toward each other.

                Each iteration performs one swap, so the whole thing takes n/2 swaps and no extra
                array. Watch the `left` and `right` pointers meet in the middle.
                """,
                Difficulty.EASY, "Arrays", Language.JAVA,
                """
                int[] arr = {1, 2, 3, 4, 5, 6};

                int left = 0;
                int right = arr.length - 1;

                while (left < right) {
                    int temp = arr[left];
                    arr[left] = arr[right];
                    arr[right] = temp;

                    left++;
                    right--;
                }

                System.out.println("Reversed: " + arr);
                """,
                null, order++));

        library.add(new Problem("bubble-sort", "Bubble Sort",
                """
                Repeatedly walk the array, swapping any pair that is out of order.

                This is the canonical visualization: the largest remaining value "bubbles" to the
                right on every pass. The swap counter is the interesting number -- for a reversed
                input it hits n(n-1)/2.
                """,
                Difficulty.EASY, "Sorting", Language.JAVA,
                """
                int[] arr = {5, 2, 8, 1, 9, 3};

                for (int i = 0; i < arr.length - 1; i++) {
                    for (int j = 0; j < arr.length - i - 1; j++) {
                        if (arr[j] > arr[j + 1]) {
                            int temp = arr[j];
                            arr[j] = arr[j + 1];
                            arr[j + 1] = temp;
                        }
                    }
                }

                System.out.println("Sorted: " + arr);
                """,
                null, order++));

        library.add(new Problem("selection-sort", "Selection Sort",
                """
                Find the smallest remaining element and swap it into place.

                Compare its counters with bubble sort on the same input: the same O(n^2)
                comparisons, but far fewer swaps -- at most one per pass.
                """,
                Difficulty.MEDIUM, "Sorting", Language.JAVA,
                """
                int[] arr = {29, 10, 14, 37, 13};

                for (int i = 0; i < arr.length - 1; i++) {
                    int minIndex = i;

                    for (int j = i + 1; j < arr.length; j++) {
                        if (arr[j] < arr[minIndex]) {
                            minIndex = j;
                        }
                    }

                    if (minIndex != i) {
                        int temp = arr[i];
                        arr[i] = arr[minIndex];
                        arr[minIndex] = temp;
                    }
                }

                System.out.println("Sorted: " + arr);
                """,
                null, order++));

        library.add(new Problem("insertion-sort", "Insertion Sort",
                """
                Grow a sorted prefix by inserting each new element into its correct position.

                Watch the shifting: elements slide right one at a time to open a gap. On
                nearly-sorted input the inner loop barely runs, which is why insertion sort is
                O(n) in the best case while bubble sort is not.
                """,
                Difficulty.MEDIUM, "Sorting", Language.JAVA,
                """
                int[] arr = {12, 11, 13, 5, 6};

                for (int i = 1; i < arr.length; i++) {
                    int key = arr[i];
                    int j = i - 1;

                    while (j >= 0 && arr[j] > key) {
                        arr[j + 1] = arr[j];
                        j = j - 1;
                    }

                    arr[j + 1] = key;
                }

                System.out.println("Sorted: " + arr);
                """,
                null, order++));

        library.add(new Problem("two-sum-brute-force", "Two Sum (Brute Force)",
                """
                Find a pair of indices whose values add up to a target.

                Every pair is tried, which is O(n^2). Once you have watched it run, the case for
                the hash-map solution makes itself.
                """,
                Difficulty.MEDIUM, "Arrays", Language.JAVA,
                """
                int[] arr = {2, 7, 11, 15, 3};
                int target = 18;

                int firstIndex = -1;
                int secondIndex = -1;

                for (int i = 0; i < arr.length; i++) {
                    for (int j = i + 1; j < arr.length; j++) {
                        if (arr[i] + arr[j] == target) {
                            firstIndex = i;
                            secondIndex = j;
                        }
                    }
                }

                System.out.println("Indices: " + firstIndex + " and " + secondIndex);
                """,
                null, order++));

        library.add(new Problem("recursive-factorial", "Recursion and the Call Stack",
                """
                Compute a factorial recursively.

                This one is about the **call stack panel**, not the array. Each `factorial(n)`
                call pushes a frame; nothing returns until the base case is reached, and then the
                results unwind back up. Step through it slowly.
                """,
                Difficulty.MEDIUM, "Recursion", Language.JAVA,
                """
                public class Main {

                    static int factorial(int n) {
                        if (n <= 1) {
                            return 1;
                        }
                        return n * factorial(n - 1);
                    }

                    public static void main(String[] args) {
                        int result = factorial(5);
                        System.out.println("5! = " + result);
                    }
                }
                """,
                null, order++));

        library.add(new Problem("bubble-sort-with-helper", "Bubble Sort with a Helper Method",
                """
                The same bubble sort, but with the swap extracted into a method.

                Worth running after the plain version: the array visualization stays the caller's
                array even while execution is inside `swap`, and the call stack panel shows the
                frame appear and disappear on every exchange.
                """,
                Difficulty.MEDIUM, "Sorting", Language.JAVA,
                """
                public class Main {

                    static void swap(int[] data, int a, int b) {
                        int temp = data[a];
                        data[a] = data[b];
                        data[b] = temp;
                    }

                    static void bubbleSort(int[] data) {
                        for (int i = 0; i < data.length - 1; i++) {
                            for (int j = 0; j < data.length - i - 1; j++) {
                                if (data[j] > data[j + 1]) {
                                    swap(data, j, j + 1);
                                }
                            }
                        }
                    }

                    public static void main(String[] args) {
                        int[] arr = {5, 2, 8, 1};
                        bubbleSort(arr);
                        System.out.println("Sorted: " + arr);
                    }
                }
                """,
                null, order++));

        library.add(new Problem("linked-list-build", "Build and Walk a Linked List",
                """
                Create four nodes, link them together, then walk the chain printing each value.

                Your first look at the **object graph** view: each `Node` is drawn as a box with
                its payload, `next` is drawn as an arrow, and the tail's `null` is drawn as the
                terminator. Step through and watch `cursor` hop along the arrows.
                """,
                Difficulty.EASY, "Linked List", Language.JAVA,
                """
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
                        Node head = new Node(1);
                        head.next = new Node(2);
                        head.next.next = new Node(3);
                        head.next.next.next = new Node(4);

                        Node cursor = head;
                        while (cursor != null) {
                            System.out.print(cursor.data + " ");
                            cursor = cursor.next;
                        }
                    }
                }
                """,
                null, order++));

        library.add(new Problem("linked-list-middle", "Find the Middle Node",
                """
                Find the middle of a list in one pass, using two pointers that move at
                different speeds.

                `slow` advances one node per step, `fast` advances two. When `fast` runs off the
                end, `slow` is exactly halfway. Watch the two pointer labels separate above the
                nodes -- that gap *is* the algorithm.
                """,
                Difficulty.EASY, "Linked List", Language.JAVA,
                """
                public class Main {

                    static class Node {
                        int data;
                        Node next;

                        Node(int data) {
                            this.data = data;
                        }
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

                        System.out.println("Middle: " + slow.data);
                    }
                }
                """,
                null, order++));

        library.add(new Problem("detect-cycle", "Detect a Cycle (Floyd's Algorithm)",
                """
                Decide whether a linked list loops back on itself, using only two pointers and
                no extra memory.

                The last node points back to the second, so the list has a cycle. The back-arrow
                is drawn as a curve underneath, which is the clearest possible picture of why
                walking this list would never terminate. `fast` gains one node on `slow` every
                iteration, so if there is a loop they must eventually land on the same node --
                and the step where `slow == fast` highlights that node.
                """,
                Difficulty.MEDIUM, "Linked List", Language.JAVA,
                """
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
                        Node head = new Node(1);
                        head.next = new Node(2);
                        head.next.next = new Node(3);
                        head.next.next.next = new Node(4);

                        // Create a cycle: node 4 points back to node 2.
                        head.next.next.next.next = head.next;

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

                        if (hasCycle) {
                            System.out.println("Cycle detected");
                        } else {
                            System.out.println("No cycle detected");
                        }
                    }
                }
                """,
                null, order++));

        library.add(new Problem("linked-list-reverse", "Reverse a Linked List",
                """
                Reverse a list in place by flipping every `next` pointer as you walk.

                The three-pointer dance (`previous`, `current`, `ahead`) is much easier to
                believe once you can see it: `ahead` saves the rest of the list before
                `current.next` is overwritten, which is the whole reason it exists. Watch the
                arrows turn around one at a time.
                """,
                Difficulty.MEDIUM, "Linked List", Language.JAVA,
                """
                public class Main {

                    static class Node {
                        int data;
                        Node next;

                        Node(int data) {
                            this.data = data;
                        }
                    }

                    public static void main(String[] args) {
                        Node head = new Node(1);
                        head.next = new Node(2);
                        head.next.next = new Node(3);
                        head.next.next.next = new Node(4);

                        Node previous = null;
                        Node current = head;

                        while (current != null) {
                            Node ahead = current.next;
                            current.next = previous;
                            previous = current;
                            current = ahead;
                        }

                        head = previous;

                        Node cursor = head;
                        while (cursor != null) {
                            System.out.print(cursor.data + " ");
                            cursor = cursor.next;
                        }
                    }
                }
                """,
                null, order++));

        library.add(new Problem("bst-insert-search", "Binary Search Tree: Insert and Search",
                """
                Build a BST by inserting keys, then search it.

                Because the node has `left` and `right` fields, the same object graph is laid
                out as a tree instead of a chain. Each insertion descends one side of the tree,
                so watch how few nodes a search actually visits -- that is the O(log n).
                """,
                Difficulty.MEDIUM, "Trees", Language.JAVA,
                """
                public class Main {

                    static class Node {
                        int key;
                        Node left;
                        Node right;

                        Node(int key) {
                            this.key = key;
                        }
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
                            if (key < root.key) {
                                root = root.left;
                            } else {
                                root = root.right;
                            }
                        }
                        return false;
                    }

                    public static void main(String[] args) {
                        Node root = insert(null, 50);
                        insert(root, 30);
                        insert(root, 70);
                        insert(root, 20);
                        insert(root, 40);
                        insert(root, 60);

                        System.out.println("Has 40: " + contains(root, 40));
                        System.out.println("Has 99: " + contains(root, 99));
                    }
                }
                """,
                null, order++));

        library.add(new Problem("tree-inorder-traversal", "In-order Traversal",
                """
                Visit every node of a BST in sorted order: left subtree, node, right subtree.

                This is the clearest use of the **call stack panel** in the library. The
                traversal is three lines long, but the order the values come out in only makes
                sense once you watch the stack grow down the left spine and unwind.
                """,
                Difficulty.MEDIUM, "Trees", Language.JAVA,
                """
                public class Main {

                    static class Node {
                        int key;
                        Node left;
                        Node right;

                        Node(int key) {
                            this.key = key;
                        }
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

                    static void inorder(Node node) {
                        if (node == null) {
                            return;
                        }
                        inorder(node.left);
                        System.out.print(node.key + " ");
                        inorder(node.right);
                    }

                    public static void main(String[] args) {
                        Node root = insert(null, 50);
                        insert(root, 30);
                        insert(root, 70);
                        insert(root, 20);
                        insert(root, 40);

                        inorder(root);
                    }
                }
                """,
                null, order++));

        return library;
    }
}
