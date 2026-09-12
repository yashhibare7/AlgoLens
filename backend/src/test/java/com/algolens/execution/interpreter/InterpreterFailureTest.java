package com.algolens.execution.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import com.algolens.entity.ExecutionStatus;
import com.algolens.execution.TestExecutor;
import com.algolens.trace.ExecutionTrace;
import com.algolens.trace.TraceAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Failure is a first-class outcome here: a learner's code is wrong most of the time, and the
 * steps leading up to the mistake are the most valuable part of the trace. So every case below
 * asserts two things -- the right status, and that the partial trace survived.
 */
class InterpreterFailureTest {

    @Test
    @DisplayName("a syntax error is a COMPILE_ERROR carrying the line number")
    void syntaxError() {
        ExecutionTrace trace = TestExecutor.run("int x = ;");

        assertThat(trace.status()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
        assertThat(trace.errorLine()).isEqualTo(1);
        assertThat(trace.steps()).isEmpty();
    }

    @Test
    @DisplayName("an out-of-bounds index keeps the steps that ran before it")
    void indexOutOfBounds() {
        ExecutionTrace trace = TestExecutor.run("""
                int[] arr = {1, 2, 3};
                int a = arr[0];
                int b = arr[5];
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("ArrayIndexOutOfBoundsException")
                .contains("Index 5");
        assertThat(trace.errorLine()).isEqualTo(3);
        assertThat(trace.steps()).isNotEmpty();
        assertThat(trace.steps().get(trace.steps().size() - 1).action())
                .isEqualTo(TraceAction.ERROR);
    }

    @Test
    @DisplayName("division by zero is reported like the JVM reports it")
    void divideByZero() {
        ExecutionTrace trace = TestExecutor.run("""
                int a = 10;
                int b = 0;
                int c = a / b;
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("/ by zero");
    }

    @Test
    @DisplayName("an unknown variable names the identifier that is missing")
    void unknownVariable() {
        ExecutionTrace trace = TestExecutor.run("int x = notDeclared + 1;");

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("notDeclared");
    }

    @Test
    @DisplayName("an infinite loop is stopped by the step budget, not by the clock")
    void infiniteLoopHitsTheStepBudget() {
        ExecutionTrace trace = TestExecutor.runWithLimits("""
                int i = 0;
                while (true) {
                    i++;
                }
                """, 500, 100_000, 10_000);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.LIMIT_EXCEEDED);
        assertThat(trace.truncated()).isTrue();
        assertThat(trace.errorMessage()).contains("infinite loop");
    }

    @Test
    @DisplayName("a long-but-finite run is truncated at the trace-event budget")
    void longRunIsTruncated() {
        ExecutionTrace trace = TestExecutor.runWithLimits("""
                int total = 0;
                for (int i = 0; i < 10000; i++) {
                    total += i;
                }
                """, 500_000, 50, 10_000);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.LIMIT_EXCEEDED);
        assertThat(trace.truncated()).isTrue();
        assertThat(trace.steps()).hasSizeLessThanOrEqualTo(51);
    }

    @Test
    @DisplayName("runaway recursion is stopped by the call-depth limit")
    void runawayRecursion() {
        ExecutionTrace trace = TestExecutor.run("""
                public class Main {
                    static int forever(int n) {
                        return forever(n + 1);
                    }

                    public static void main(String[] args) {
                        forever(0);
                    }
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.LIMIT_EXCEEDED);
        assertThat(trace.errorMessage()).containsIgnoringCase("recursion");
    }

    @Test
    @DisplayName("an oversized allocation is refused before it is made")
    void oversizedAllocation() {
        ExecutionTrace trace = TestExecutor.run("int[] huge = new int[999999999];");

        assertThat(trace.status()).isEqualTo(ExecutionStatus.LIMIT_EXCEEDED);
        assertThat(trace.errorMessage()).contains("exceeds the limit");
    }

    @Test
    @DisplayName("narrowing without a cast is rejected, as javac would reject it")
    void narrowingRequiresACast() {
        ExecutionTrace trace = TestExecutor.run("""
                double value = 3.9;
                int truncated = value;
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("cast");
    }

    @Test
    @DisplayName("an explicit cast truncates exactly as Java does")
    void explicitCastTruncates() {
        ExecutionTrace trace = TestExecutor.run("""
                double value = 3.9;
                int truncated = (int) value;
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    @DisplayName("integer division and int overflow match the JVM")
    void integerSemanticsMatchTheJvm() {
        ExecutionTrace trace = TestExecutor.run("""
                int half = 7 / 2;
                int wrapped = 2147483647 + 1;
                System.out.println(half + " " + wrapped);
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("3 -2147483648");
    }

    @Test
    @DisplayName("a non-boolean condition explains that Java has no truthy values")
    void nonBooleanCondition() {
        ExecutionTrace trace = TestExecutor.run("""
                int x = 1;
                if (x) {
                    x = 2;
                }
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(trace.errorMessage()).contains("boolean");
    }

    @Test
    @DisplayName("a fully qualified type is the same type, not an error")
    void fullyQualifiedTypesWork() {
        ExecutionTrace trace = TestExecutor.run("""
                java.util.List<Integer> list = new java.util.ArrayList<Integer>();
                list.add(7);
                System.out.println(list);
                """);

        assertThat(trace.status())
                .as("failed: %s", trace.errorMessage())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.stdout().strip()).isEqualTo("[7]");
    }

    @Test
    @DisplayName("a lambda is refused by name rather than reported as a mystery")
    void unsupportedSyntaxIsNamed() {
        ExecutionTrace trace = TestExecutor.run("""
                import java.util.*;
                List<Integer> list = new ArrayList<>();
                list.removeIf(x -> x > 2);
                """);

        assertThat(trace.status()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
        assertThat(trace.errorMessage()).isNotBlank();
    }
}
