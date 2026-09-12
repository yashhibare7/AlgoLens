package com.algolens.judge;

import com.algolens.config.AppProperties;
import com.algolens.dto.judge.SubmissionResponse;
import com.algolens.entity.CreditTransactionType;
import com.algolens.entity.ExecutionStatus;
import com.algolens.entity.Language;
import com.algolens.entity.Problem;
import com.algolens.entity.TestCase;
import com.algolens.exception.BadRequestException;
import com.algolens.exception.InsufficientCreditsException;
import com.algolens.exception.UnauthenticatedException;
import com.algolens.execution.CodeExecutor;
import com.algolens.execution.ExecutionRequest;
import com.algolens.execution.ExecutionSandbox;
import com.algolens.execution.ExecutorRegistry;
import com.algolens.repository.TestCaseRepository;
import com.algolens.service.CreditService;
import com.algolens.service.ProblemService;
import com.algolens.trace.ExecutionTrace;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one submission: build the driver program, run it through the same
 * {@link CodeExecutor} the visualizer uses, and turn its stdout back into a per-test verdict via
 * {@link JudgeEvaluator}.
 *
 * <p>Deliberately not {@code @Transactional} as a whole, for the same reason as
 * {@link com.algolens.service.ExecutionService}: interpreting can take seconds, and holding a
 * connection open for that long is how a pool dies under load.
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final ExecutorRegistry executors;
    private final ExecutionSandbox sandbox;
    private final CreditService credits;
    private final ProblemService problemService;
    private final TestCaseRepository testCaseRepository;
    private final SubmissionRecorder recorder;
    private final AppProperties.Execution limits;

    public JudgeService(ExecutorRegistry executors, ExecutionSandbox sandbox, CreditService credits,
            ProblemService problemService, TestCaseRepository testCaseRepository,
            SubmissionRecorder recorder, AppProperties properties) {
        this.executors = executors;
        this.sandbox = sandbox;
        this.credits = credits;
        this.problemService = problemService;
        this.testCaseRepository = testCaseRepository;
        this.recorder = recorder;
        this.limits = properties.execution();
    }

    /**
     * @param userId the caller, or null for an anonymous submission
     */
    public SubmissionResponse submit(Long userId, String slugOrId, String code) {
        if (userId == null && !limits.allowAnonymous()) {
            throw new UnauthenticatedException("Sign in to submit a solution");
        }

        Problem problem = problemService.find(slugOrId);
        if (!problem.isJudgeEnabled()) {
            throw new BadRequestException(
                    "'" + problem.getTitle() + "' does not have automated judging yet.");
        }
        List<TestCase> testCases = testCaseRepository
                .findByProblemIdOrderByDisplayOrderAsc(problem.getId());
        if (testCases.isEmpty()) {
            throw new BadRequestException(
                    "'" + problem.getTitle() + "' has no test cases configured yet.");
        }

        CodeExecutor executor = executors.forLanguage(Language.JAVA)
                .orElseThrow(() -> new IllegalStateException("Java executor is not registered"));

        int cost = credits.config().costExecution();
        assertAffordable(userId, cost);

        String harness = JudgeHarnessBuilder.build(problem, testCases, code);
        ExecutionTrace trace = runGuarded(executor, new ExecutionRequest(Language.JAVA, harness));
        JudgeResult result = JudgeEvaluator.evaluate(trace, testCases);

        int creditsSpent = chargeIfRan(userId, trace, cost);
        Integer balance = userId == null ? null : credits.getBalance(userId);
        Long submissionId = userId == null ? null : recordSafely(userId, problem, code, result);

        return new SubmissionResponse(submissionId, result, creditsSpent, balance);
    }

    private ExecutionTrace runGuarded(CodeExecutor executor, ExecutionRequest request) {
        try {
            return sandbox.run(() -> executor.execute(request), limits.wallClockTimeoutMs());
        } catch (ExecutionSandbox.SandboxTimeoutException e) {
            return ExecutionTrace.failure(request.language().name(), ExecutionStatus.TIMEOUT,
                    e.getMessage(), null, limits.wallClockTimeoutMs());
        } catch (RuntimeException e) {
            log.error("Judging failed outside the executor's own error handling", e);
            return ExecutionTrace.failure(request.language().name(),
                    ExecutionStatus.INTERNAL_ERROR, "AlgoLens could not judge this submission.",
                    null, 0);
        }
    }

    private void assertAffordable(Long userId, int cost) {
        if (userId == null || !credits.config().enforced() || cost <= 0) {
            return;
        }
        int balance = credits.getBalance(userId);
        if (balance < cost) {
            throw new InsufficientCreditsException(cost, balance);
        }
    }

    /** Mirrors {@code ExecutionService}: a submission that never compiled costs nothing. */
    private int chargeIfRan(Long userId, ExecutionTrace trace, int cost) {
        if (userId == null || cost <= 0 || trace.status() == ExecutionStatus.COMPILE_ERROR
                || trace.status() == ExecutionStatus.UNSUPPORTED_LANGUAGE) {
            return 0;
        }
        credits.consume(userId, cost, CreditTransactionType.CODE_SUBMISSION,
                "Submitted a solution (" + trace.totalSteps() + " steps)");
        return cost;
    }

    private Long recordSafely(Long userId, Problem problem, String code, JudgeResult result) {
        try {
            return recorder.record(userId, problem, code, result);
        } catch (RuntimeException e) {
            log.error("Could not record submission for user {}", userId, e);
            return null;
        }
    }
}
