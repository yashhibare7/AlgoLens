package com.algolens.execution.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.algolens.entity.ExecutionStatus;
import com.algolens.execution.TestExecutor;
import com.algolens.trace.ExecutionTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The LeetCode submission shape: a bare {@code class Solution} with one method and no
 * {@code main}.
 *
 * <p>This is the single most common thing anyone pastes into a DSA tool, so it gets its own
 * test. Two separate things have to hold: the executor must never throw (a failure is a trace
 * with a status, not an exception), and the resulting trace must survive JSON serialisation --
 * an exception thrown while Jackson writes the response surfaces as an HTTP 500 long after the
 * executor has done its job correctly.
 */
class LeetCodeShapeTest {

    private static final String ORANGES_ROTTING = """
            class Solution {
                public int orangesRotting(int[][] grid) {
                    if (grid == null || grid.length == 0) return 0;

                    int rows = grid.length;
                    int cols = grid[0].length;
                    java.util.Queue<int[]> queue = new java.util.LinkedList<>();
                    int freshCount = 0;

                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            if (grid[r][c] == 2) {
                                queue.offer(new int[]{r, c});
                            } else if (grid[r][c] == 1) {
                                freshCount++;
                            }
                        }
                    }

                    if (freshCount == 0) return 0;

                    int minutes = 0;
                    int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

                    while (!queue.isEmpty()) {
                        int size = queue.size();
                        boolean rottedAny = false;

                        for (int i = 0; i < size; i++) {
                            int[] current = queue.poll();
                            int r = current[0];
                            int c = current[1];

                            for (int[] dir : directions) {
                                int nr = r + dir[0];
                                int nc = c + dir[1];

                                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                                        && grid[nr][nc] == 1) {
                                    grid[nr][nc] = 2;
                                    queue.offer(new int[]{nr, nc});
                                    freshCount--;
                                    rottedAny = true;
                                }
                            }
                        }

                        if (rottedAny) {
                            minutes++;
                        }
                    }

                    return freshCount == 0 ? minutes : -1;
                }
            }
            """;

    @Test
    @DisplayName("a bare Solution class never throws, and never becomes an internal error")
    void solutionClassIsHandledCleanly() {
        ExecutionTrace trace = TestExecutor.run(ORANGES_ROTTING);

        assertThat(trace.status())
                .as("a submission with no main is a user-fixable problem, not our bug")
                .isEqualTo(ExecutionStatus.COMPILE_ERROR);
        assertThat(trace.errorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("the message tells the user exactly what to add")
    void theMessageIsActionable() {
        ExecutionTrace trace = TestExecutor.run(ORANGES_ROTTING);

        // Naming the method and handing over a runnable main turns a dead end into a paste.
        assertThat(trace.errorMessage())
                .contains("main")
                .contains("orangesRotting")
                .contains("new Solution()");
    }

    @Test
    @DisplayName("the same code with a main runs, and gets the right answer")
    void withAMainItRuns() {
        String withMain = ORANGES_ROTTING.stripTrailing();
        withMain = withMain.substring(0, withMain.lastIndexOf('}')) + """

                    public static void main(String[] args) {
                        int[][] grid = {{2, 1, 1}, {1, 1, 0}, {0, 1, 1}};
                        System.out.println(new Solution().orangesRotting(grid));
                    }
                }
                """;

        ExecutionTrace trace = TestExecutor.run(withMain);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("4");
    }

    @Test
    @DisplayName("a trace holding int[] inside a Queue serialises to JSON")
    void traceWithArraysInsideCollectionsSerialises() throws Exception {
        // The API returns the trace as JSON. A raw interpreter value leaking into a trace event
        // would throw here -- and over HTTP that is a 500 raised *after* the executor succeeded,
        // which is exactly the failure this test exists to catch.
        ExecutionTrace trace = TestExecutor.run("""
                import java.util.*;

                Queue<int[]> queue = new LinkedList<>();
                queue.offer(new int[]{1, 2});
                queue.offer(new int[]{3, 4});
                int[] first = queue.poll();

                Map<String, int[]> byName = new HashMap<>();
                byName.put("a", new int[]{9});

                List<List<Integer>> nested = new ArrayList<>();
                List<Integer> inner = new ArrayList<>();
                inner.add(7);
                nested.add(inner);

                System.out.println(first[0] + " " + byName.get("a")[0] + " " + nested.get(0).get(0));
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("1 9 7");

        ObjectMapper mapper = new ObjectMapper();
        assertThatCode(() -> mapper.writeValueAsString(trace)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("iterating a 2D array yields rows, and indexing them works")
    void forEachOverRowsOfAMatrix() {
        ExecutionTrace trace = TestExecutor.run("""
                int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                int sum = 0;
                for (int[] dir : directions) {
                    sum = sum + dir[0] + dir[1];
                }
                System.out.println(sum);
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("0");
    }

    @Test
    @DisplayName("the generated main actually compiles and runs when pasted back in")
    void theGeneratedMainIsRunnable() {
        ExecutionTrace refused = TestExecutor.run("""
                class Solution {
                    public int twice(int n) {
                        return n * 2;
                    }
                }
                """);

        // Pull the suggested main straight out of the error and paste it into the class,
        // exactly as a user would.
        String suggestion = refused.errorMessage();
        int start = suggestion.indexOf("    public static void main");
        assertThat(start).as("the message should contain a main to paste").isPositive();
        String generatedMain = suggestion.substring(start);

        ExecutionTrace trace = TestExecutor.run("""
                class Solution {
                    public int twice(int n) {
                        return n * 2;
                    }

                %s
                }
                """.formatted(generatedMain));

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("10");
    }

    @Test
    @DisplayName("a static method can be called through its class name")
    void staticMethodThroughClassName() {
        ExecutionTrace trace = TestExecutor.run("""
                class Solution {
                    static int add(int a, int b) {
                        return a + b;
                    }

                    public static void main(String[] args) {
                        System.out.println(Solution.add(2, 3));
                    }
                }
                """);

        assertThat(trace.status())
                .as("failed: %s (line %s)", trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("5");
    }

    @Test
    @DisplayName("calling an instance method statically says how to fix it")
    void instanceMethodCalledStatically() {
        ExecutionTrace trace = TestExecutor.run("""
                class Solution {
                    public int twice(int n) { return n * 2; }

                    public static void main(String[] args) {
                        System.out.println(Solution.twice(4));
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("new Solution().twice");
    }

    @Test
    @DisplayName("a class with several methods and no main still explains itself")
    void multipleMethodsNoMain() {
        ExecutionTrace trace = TestExecutor.run("""
                class Solution {
                    public int add(int a, int b) { return a + b; }
                    public int mul(int a, int b) { return a * b; }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
        assertThat(trace.errorMessage()).contains("main");
    }
}
