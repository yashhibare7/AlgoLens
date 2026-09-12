package com.algolens.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.algolens.entity.Problem;
import com.algolens.entity.TestCase;
import com.algolens.entity.Verdict;
import com.algolens.execution.TestExecutor;
import com.algolens.judge.JudgeEvaluator;
import com.algolens.judge.JudgeHarnessBuilder;
import com.algolens.judge.JudgeResult;
import com.algolens.trace.ExecutionTrace;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every judge-enabled seeded problem's *reference solution* must pass its own test cases.
 *
 * <p>This is the judge-problem counterpart to {@link ProblemLibraryTest}: it is what catches a
 * hand-computed expected output that is actually wrong, or a reference solution that does not
 * parse under the interpreter's subset, before either ships.
 */
class JudgeSeedSolutionsTest {

    @Test
    void twoSumSolutionPassesItsOwnTestCases() {
        Problem problem = findJudgeProblem("two-sum");
        assertAccepted(problem, ProblemSeeder.twoSumTestCases(problem));
    }

    @Test
    void validParenthesesSolutionPassesItsOwnTestCases() {
        Problem problem = findJudgeProblem("valid-parentheses");
        assertAccepted(problem, ProblemSeeder.validParenthesesTestCases(problem));
    }

    private static void assertAccepted(Problem problem, List<TestCase> cases) {
        String harness = JudgeHarnessBuilder.build(problem, cases, problem.getSolutionCode());
        ExecutionTrace trace = TestExecutor.run(harness);
        JudgeResult result = JudgeEvaluator.evaluate(trace, cases);

        assertThat(result.verdict())
                .as("'%s' reference solution did not pass its own test cases (judge error: %s, "
                        + "trace error: %s)", problem.getSlug(), result.errorMessage(),
                        trace.errorMessage())
                .isEqualTo(Verdict.ACCEPTED);
        assertThat(result.passedCount())
                .as("'%s' should pass every one of its test cases", problem.getSlug())
                .isEqualTo(cases.size());
    }

    private static Problem findJudgeProblem(String slug) {
        return ProblemSeeder.library().stream().filter(p -> p.getSlug().equals(slug)).findFirst()
                .orElseThrow(() -> new AssertionError("No seeded problem with slug " + slug));
    }
}
