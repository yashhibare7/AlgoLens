package com.algolens.judge;

import com.algolens.entity.ExecutionStatus;
import com.algolens.entity.TestCase;
import com.algolens.entity.Verdict;
import com.algolens.trace.ExecutionTrace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a driver run's {@link ExecutionTrace} back into a {@link JudgeResult}.
 *
 * <p>Pulled out of {@link JudgeService} so it has no Spring dependency and can be exercised
 * directly against the interpreter in tests (see {@code JudgeSeedSolutionsTest}), the same way
 * {@code TestExecutor} lets other tests run code without a Spring context.
 */
public final class JudgeEvaluator {

    private static final Pattern MARKER_LINE = Pattern.compile(
            "^" + Pattern.quote(JudgeHarnessBuilder.MARKER_PREFIX) + "(\\d+)_(OK|ERR)##(.*)$");

    private JudgeEvaluator() {
    }

    /**
     * Reads the driver's marker lines back out of stdout and compares each against its test
     * case's expected output. A test case whose marker never appears (the run was cut off by a
     * limit before reaching it) is left out of {@code outcomes} entirely rather than guessed at.
     */
    public static JudgeResult evaluate(ExecutionTrace trace, List<TestCase> testCases) {
        Map<Integer, TestCaseOutcome> byIndex = new LinkedHashMap<>();
        if (trace.stdout() != null) {
            for (String line : trace.stdout().split("\n", -1)) {
                Matcher matcher = MARKER_LINE.matcher(line.stripTrailing());
                if (!matcher.matches()) {
                    continue;
                }
                int index = Integer.parseInt(matcher.group(1));
                if (index < 0 || index >= testCases.size()) {
                    continue;
                }
                TestCase testCase = testCases.get(index);
                boolean ok = "OK".equals(matcher.group(2));
                String payload = matcher.group(3);
                if (ok) {
                    boolean passed = payload.trim().equals(testCase.getExpectedOutput().trim());
                    byIndex.put(index, new TestCaseOutcome(index, testCase.isSample(), passed,
                            testCase.isSample() ? payload.trim() : null,
                            testCase.isSample() ? testCase.getExpectedOutput() : null, null));
                } else {
                    byIndex.put(index, new TestCaseOutcome(index, testCase.isSample(), false,
                            null, null, payload));
                }
            }
        }

        List<TestCaseOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < testCases.size(); i++) {
            TestCaseOutcome outcome = byIndex.get(i);
            if (outcome != null) {
                outcomes.add(outcome);
            }
        }

        int passedCount = (int) outcomes.stream().filter(TestCaseOutcome::passed).count();
        Verdict verdict = determineVerdict(trace, outcomes, testCases.size());
        String errorMessage = verdict.isAccepted() ? null : firstErrorMessage(outcomes, trace);
        return new JudgeResult(verdict, passedCount, testCases.size(), outcomes, errorMessage,
                trace.durationMs());
    }

    private static Verdict determineVerdict(ExecutionTrace trace, List<TestCaseOutcome> outcomes,
            int totalTests) {
        if (trace.status() == ExecutionStatus.COMPILE_ERROR) {
            return Verdict.COMPILE_ERROR;
        }
        if (outcomes.isEmpty()) {
            return switch (trace.status()) {
                case TIMEOUT, LIMIT_EXCEEDED -> Verdict.TIME_LIMIT_EXCEEDED;
                case UNSUPPORTED_LANGUAGE, INTERNAL_ERROR -> Verdict.INTERNAL_ERROR;
                default -> Verdict.RUNTIME_ERROR;
            };
        }
        boolean anyError = outcomes.stream().anyMatch(o -> o.errorMessage() != null);
        if (anyError) {
            return Verdict.RUNTIME_ERROR;
        }
        boolean anyWrong = outcomes.stream().anyMatch(o -> !o.passed());
        if (anyWrong) {
            return Verdict.WRONG_ANSWER;
        }
        if (outcomes.size() < totalTests) {
            return Verdict.TIME_LIMIT_EXCEEDED;
        }
        return Verdict.ACCEPTED;
    }

    private static String firstErrorMessage(List<TestCaseOutcome> outcomes, ExecutionTrace trace) {
        return outcomes.stream().map(TestCaseOutcome::errorMessage).filter(m -> m != null)
                .findFirst().orElse(trace.errorMessage());
    }
}
