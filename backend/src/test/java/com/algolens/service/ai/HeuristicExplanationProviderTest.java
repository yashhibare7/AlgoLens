package com.algolens.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.algolens.dto.ai.ExplainMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The local explainer must never state something the code does not support. It is the answer a
 * user sees when no model is configured, so a confident wrong claim here is worse than a vague
 * right one.
 */
class HeuristicExplanationProviderTest {

    private final HeuristicExplanationProvider provider = new HeuristicExplanationProvider();

    @Test
    @DisplayName("nested loops are quadratic and are not reported as recursion")
    void nestedLoopsAreNotRecursion() {
        String explanation = provider.explain(prompt(ExplainMode.COMPLEXITY, """
                int[] a = {3, 1};
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        int x = a[j];
                    }
                }
                """));

        assertThat(explanation).contains("O(n^2)");
        assertThat(explanation).doesNotContain("recurse");
    }

    @Test
    @DisplayName("a single loop is linear")
    void singleLoopIsLinear() {
        String explanation = provider.explain(prompt(ExplainMode.COMPLEXITY, """
                int total = 0;
                for (int i = 0; i < 10; i++) {
                    total += i;
                }
                """));

        assertThat(explanation).contains("O(n)").doesNotContain("O(n^2)");
    }

    @Test
    @DisplayName("genuine self-recursion is detected")
    void detectsRealRecursion() {
        String explanation = provider.explain(prompt(ExplainMode.COMPLEXITY, """
                public class Main {
                    static int factorial(int n) {
                        if (n <= 1) {
                            return 1;
                        }
                        return n * factorial(n - 1);
                    }

                    public static void main(String[] args) {
                        factorial(5);
                    }
                }
                """));

        assertThat(explanation).contains("recurse");
    }

    @Test
    @DisplayName("every answer is labelled as a local analysis")
    void answersAreLabelled() {
        for (ExplainMode mode : ExplainMode.values()) {
            assertThat(provider.explain(prompt(mode, "int x = 1;")))
                    .as("mode %s", mode)
                    .contains("Local analysis");
        }
    }

    @Test
    @DisplayName("explaining a step with no trace says so instead of inventing one")
    void stepWithoutTraceIsHonest() {
        String explanation = provider.explain(
                new ExplanationPrompt(ExplainMode.EXPLAIN_STEP, "Java", "int x = 1;", 0, List.of(),
                        null));

        assertThat(explanation).contains("no trace");
    }

    private static ExplanationPrompt prompt(ExplainMode mode, String code) {
        return new ExplanationPrompt(mode, "Java", code, 1,
                List.of("step 1 | line 1 | DECLARE | int x = 1"), null);
    }
}
