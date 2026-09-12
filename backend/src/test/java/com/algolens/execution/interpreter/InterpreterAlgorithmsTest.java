package com.algolens.execution.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import com.algolens.entity.ExecutionStatus;
import com.algolens.execution.TestExecutor;
import com.algolens.trace.ArrayElement;
import com.algolens.trace.ExecutionTrace;
import com.algolens.trace.TraceAction;
import com.algolens.trace.TraceEvent;
import com.algolens.trace.VisualizationState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tests that matter most: the MVP algorithms must produce correct <em>results</em> and
 * correct <em>traces</em>. A visualizer whose picture disagrees with the code is worse than no
 * visualizer, so these assert the final data state, the recorded metrics, and that the swap
 * collapsing actually fires.
 */
class InterpreterAlgorithmsTest {

    // ------------------------------------------------------------------ results

    @Test
    @DisplayName("bubble sort sorts the array and records swaps")
    void bubbleSort() {
        ExecutionTrace trace = TestExecutor.run("""
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
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(finalArray(trace, "arr")).containsExactly(1, 2, 3, 5, 8, 9);
        assertThat(trace.metrics().swaps()).isGreaterThan(0);
        // 6 elements, one comparison per inner iteration: 5+4+3+2+1 = 15
        assertThat(trace.metrics().comparisons()).isEqualTo(15);
    }

    @Test
    @DisplayName("selection sort sorts the array")
    void selectionSort() {
        ExecutionTrace trace = TestExecutor.run("""
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
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(finalArray(trace, "arr")).containsExactly(10, 13, 14, 29, 37);
        assertThat(trace.metrics().comparisons()).isEqualTo(10);
    }

    @Test
    @DisplayName("insertion sort sorts the array")
    void insertionSort() {
        ExecutionTrace trace = TestExecutor.run("""
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
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(finalArray(trace, "arr")).containsExactly(5, 6, 11, 12, 13);
    }

    @Test
    @DisplayName("linear search finds the target and stops early")
    void linearSearch() {
        ExecutionTrace trace = TestExecutor.run("""
                int[] arr = {4, 8, 15, 16, 23, 42};
                int target = 16;
                int foundAt = -1;

                for (int i = 0; i < arr.length; i++) {
                    if (arr[i] == target) {
                        foundAt = i;
                        break;
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(finalVariable(trace, "foundAt")).isEqualTo(3);
        // Stopped at index 3, so exactly 4 comparisons happened -- proof the break works.
        assertThat(trace.metrics().comparisons()).isEqualTo(4);
    }

    @Test
    @DisplayName("binary search finds the target in logarithmic comparisons")
    void binarySearch() {
        ExecutionTrace trace = TestExecutor.run("""
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
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(finalVariable(trace, "foundAt")).isEqualTo(5);
        assertThat(trace.metrics().comparisons()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("reverse in place swaps from both ends")
    void reverseArray() {
        ExecutionTrace trace = TestExecutor.run("""
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
                """);

        assertThat(finalArray(trace, "arr")).containsExactly(6, 5, 4, 3, 2, 1);
        assertThat(trace.metrics().swaps()).isEqualTo(3);
    }

    // ------------------------------------------------------------------ trace quality

    @Test
    @DisplayName("the three-statement temp swap is collapsed into one SWAP step")
    void detectsSwaps() {
        ExecutionTrace trace = TestExecutor.run("""
                int[] arr = {2, 1};
                int temp = arr[0];
                arr[0] = arr[1];
                arr[1] = temp;
                """);

        List<TraceEvent> swaps = trace.steps().stream()
                .filter(step -> step.action() == TraceAction.SWAP)
                .toList();

        assertThat(swaps).hasSize(1);
        assertThat(swaps.get(0).message()).contains("Swapped arr[0] and arr[1]");
        assertThat(finalArray(trace, "arr")).containsExactly(1, 2);
    }

    @Test
    @DisplayName("a lookalike sequence that does not exchange values is not called a swap")
    void doesNotInventSwaps() {
        // Same index shape as a swap, but the last write stores an unrelated value.
        ExecutionTrace trace = TestExecutor.run("""
                int[] arr = {2, 1, 7};
                int temp = arr[0];
                arr[0] = arr[1];
                arr[1] = 99;
                """);

        assertThat(trace.steps()).noneMatch(step -> step.action() == TraceAction.SWAP);
        assertThat(finalArray(trace, "arr")).containsExactly(1, 99, 7);
    }

    @Test
    @DisplayName("comparing two array cells is a COMPARE with both cells highlighted")
    void comparisonsHighlightBothCells() {
        ExecutionTrace trace = TestExecutor.run("""
                int[] arr = {5, 2};
                if (arr[0] > arr[1]) {
                    int x = 1;
                }
                """);

        TraceEvent compare = trace.steps().stream()
                .filter(step -> step.action() == TraceAction.COMPARE)
                .findFirst()
                .orElseThrow();

        assertThat(compare.message()).contains("arr[0]=5").contains("arr[1]=2").contains("true");
        assertThat(compare.touches()).isNotNull();
        assertThat(compare.touches()).anyMatch(touch -> touch.index() == 0);
        assertThat(compare.touches()).anyMatch(touch -> touch.index() == 1);
    }

    @Test
    @DisplayName("a loop bound is a CONDITION, not counted as an element comparison")
    void loopBoundsAreNotElementComparisons() {
        ExecutionTrace trace = TestExecutor.run("""
                int[] arr = {1, 2, 3};
                int total = 0;
                for (int i = 0; i < arr.length; i++) {
                    total += arr[i];
                }
                """);

        assertThat(trace.metrics().comparisons()).isZero();
        assertThat(trace.steps()).anyMatch(step -> step.action() == TraceAction.CONDITION);
        assertThat(finalVariable(trace, "total")).isEqualTo(6);
    }

    @Test
    @DisplayName("index variables are drawn as pointers under the array")
    void exposesPointers() {
        ExecutionTrace trace = TestExecutor.run("""
                int[] arr = {1, 2, 3, 4};
                int low = 0;
                int high = 3;
                int mid = 1;
                int touched = arr[mid];
                """);

        VisualizationState array = lastArray(trace, "arr");
        assertThat(array.pointers()).isNotNull();
        assertThat(array.pointers()).extracting("name")
                .contains("low", "high", "mid");
    }

    @Test
    @DisplayName("every step carries the source line it executed")
    void everyStepHasALine() {
        ExecutionTrace trace = TestExecutor.run("""
                int a = 1;
                int b = 2;
                int c = a + b;
                """);

        assertThat(trace.steps()).allSatisfy(step -> assertThat(step.line()).isPositive());
        assertThat(trace.steps().get(0).action()).isEqualTo(TraceAction.START);
        assertThat(trace.steps().get(trace.steps().size() - 1).action())
                .isEqualTo(TraceAction.DONE);
    }

    // ------------------------------------------------------------------ methods & recursion

    @Test
    @DisplayName("a helper method that swaps operates on the caller's array")
    void helperMethodSharesTheCallersArray() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static void swap(int[] data, int a, int b) {
                        int temp = data[a];
                        data[a] = data[b];
                        data[b] = temp;
                    }

                    public static void main(String[] args) {
                        int[] arr = {9, 4};
                        swap(arr, 0, 1);
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(finalArray(trace, "arr")).containsExactly(4, 9);
        // The parameter is called 'data', but the picture must stay the caller's 'arr'.
        assertThat(trace.steps()).anyMatch(step -> step.visualizations() != null
                && step.visualizations().stream().anyMatch(v -> "arr".equals(v.name())));
    }

    @Test
    @DisplayName("recursion records CALL and RETURN steps and reports call depth")
    void recursionBuildsACallStack() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static int factorial(int n) {
                        if (n <= 1) {
                            return 1;
                        }
                        return n * factorial(n - 1);
                    }

                    public static void main(String[] args) {
                        int result = factorial(5);
                        System.out.println(result);
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("120");
        assertThat(trace.metrics().calls()).isEqualTo(5);
        assertThat(trace.metrics().maxCallDepth()).isEqualTo(5);
        assertThat(trace.steps()).anyMatch(step -> step.action() == TraceAction.CALL);
        assertThat(trace.steps()).anyMatch(step -> step.action() == TraceAction.RETURN);
    }

    // ------------------------------------------------------------------ output & helpers

    @Test
    @DisplayName("printing an array shows its contents and lands on the step that printed")
    void capturesOutput() {
        ExecutionTrace trace = TestExecutor.run("""
                int[] arr = {3, 1, 2};
                System.out.println("Values: " + arr);
                System.out.println("Length: " + arr.length);
                """);

        assertThat(trace.stdout()).isEqualTo("Values: [3, 1, 2]\nLength: 3\n");
        assertThat(trace.steps()).anyMatch(step -> step.action() == TraceAction.OUTPUT
                && step.output() != null && step.output().contains("Values"));
    }

    @Test
    @DisplayName("Math and String built-ins work")
    void supportsBuiltins() {
        ExecutionTrace trace = TestExecutor.run("""
                int biggest = Math.max(3, 9);
                double root = Math.sqrt(16.0);
                String word = "AlgoLens";
                int length = word.length();
                char first = word.charAt(0);
                System.out.println(biggest + " " + root + " " + length + " " + first);
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("9 4.0 8 A");
    }

    @Test
    @DisplayName("for-each iterates and highlights each element read")
    void supportsForEach() {
        ExecutionTrace trace = TestExecutor.run("""
                int[] numbers = {4, 8, 15};
                int sum = 0;
                for (int value : numbers) {
                    sum = sum + value;
                }
                """);

        assertThat(finalVariable(trace, "sum")).isEqualTo(27);
        assertThat(trace.metrics().arrayReads()).isEqualTo(3);
    }

    @Test
    @DisplayName("new int[n] allocates zeroed elements")
    void supportsNewArray() {
        ExecutionTrace trace = TestExecutor.run("""
                int[] counts = new int[4];
                counts[2] = 7;
                """);

        assertThat(finalArray(trace, "counts")).containsExactly(0, 0, 7, 0);
    }

    // ------------------------------------------------------------------ assertions helpers

    private static List<Object> finalArray(ExecutionTrace trace, String name) {
        return lastArray(trace, name).elements().stream().map(ArrayElement::value).toList();
    }

    private static VisualizationState lastArray(ExecutionTrace trace, String name) {
        for (int i = trace.steps().size() - 1; i >= 0; i--) {
            List<VisualizationState> visualizations = trace.steps().get(i).visualizations();
            if (visualizations == null) {
                continue;
            }
            for (VisualizationState visualization : visualizations) {
                if (name.equals(visualization.name())) {
                    return visualization;
                }
            }
        }
        throw new AssertionError("No visualization named '" + name + "' in the trace");
    }

    private static Object finalVariable(ExecutionTrace trace, String name) {
        for (int i = trace.steps().size() - 1; i >= 0; i--) {
            if (trace.steps().get(i).variables() == null) {
                continue;
            }
            for (var variable : trace.steps().get(i).variables()) {
                if (name.equals(variable.name())) {
                    return variable.value();
                }
            }
        }
        throw new AssertionError("No variable named '" + name + "' in the trace");
    }
}
