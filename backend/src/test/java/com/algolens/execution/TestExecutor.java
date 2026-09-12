package com.algolens.execution;

import com.algolens.config.AppProperties;
import com.algolens.entity.Language;
import com.algolens.trace.ExecutionTrace;

/** Builds a {@link JavaSubsetExecutor} with generous test limits. */
public final class TestExecutor {

    private TestExecutor() {
    }

    public static ExecutionTrace run(String code) {
        return executor().execute(new ExecutionRequest(Language.JAVA, code));
    }

    public static JavaSubsetExecutor executor() {
        return new JavaSubsetExecutor(properties(new AppProperties.Execution(
                true, 20000, 500_000, 50_000, 10_000, 20_000, 200, 10_000, 3_000_000)));
    }

    /** A tighter budget, for asserting that limits actually bite. */
    public static ExecutionTrace runWithLimits(String code, long maxSteps, int maxEvents,
            long timeoutMs) {
        JavaSubsetExecutor executor = new JavaSubsetExecutor(properties(
                new AppProperties.Execution(true, 20000, maxSteps, maxEvents, timeoutMs, 20_000,
                        200, 10_000, 3_000_000)));
        return executor.execute(new ExecutionRequest(Language.JAVA, code));
    }

    private static AppProperties properties(AppProperties.Execution execution) {
        return new AppProperties(
                new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Jwt("test-secret-that-is-long-enough-for-hs256-abcdefgh",
                        "algolens", 60),
                execution,
                new AppProperties.Credits(false, 100, 100, 1, 5, 10),
                new AppProperties.Ai(false, "", "claude-opus-5", 1000, "MEDIUM", 30));
    }
}
