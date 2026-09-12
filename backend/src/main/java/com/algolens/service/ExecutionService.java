package com.algolens.service;

import com.algolens.config.AppProperties;
import com.algolens.dto.execution.ExecuteCodeRequest;
import com.algolens.dto.execution.ExecutionResponse;
import com.algolens.entity.CreditTransactionType;
import com.algolens.entity.ExecutionStatus;
import com.algolens.entity.Language;
import com.algolens.exception.BadRequestException;
import com.algolens.exception.InsufficientCreditsException;
import com.algolens.exception.UnauthenticatedException;
import com.algolens.execution.CodeExecutor;
import com.algolens.execution.ExecutionRequest;
import com.algolens.execution.ExecutionSandbox;
import com.algolens.execution.ExecutorRegistry;
import com.algolens.trace.ExecutionTrace;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one run: authorize, meter, execute, record.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional} as a whole. Interpreting user code can take
 * seconds, and holding a database connection plus an open transaction for that long is how a
 * connection pool dies under load. Each database interaction is its own short transaction
 * ({@link CreditService}, {@link ExecutionRecorder}), with execution happening between them.
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutorRegistry executors;
    private final ExecutionSandbox sandbox;
    private final CreditService credits;
    private final ExecutionRecorder recorder;
    private final AppProperties.Execution limits;

    public ExecutionService(ExecutorRegistry executors, ExecutionSandbox sandbox,
            CreditService credits, ExecutionRecorder recorder, AppProperties properties) {
        this.executors = executors;
        this.sandbox = sandbox;
        this.credits = credits;
        this.recorder = recorder;
        this.limits = properties.execution();
    }

    /**
     * @param userId the caller, or null for an anonymous run
     */
    public ExecutionResponse execute(Long userId, ExecuteCodeRequest request) {
        if (userId == null && !limits.allowAnonymous()) {
            throw new UnauthenticatedException("Sign in to run code");
        }
        Language language = Language.fromString(request.language())
                .orElseThrow(() -> new BadRequestException(
                        "Unknown language '" + request.language() + "'"));

        Optional<CodeExecutor> executor = executors.forLanguage(language);
        if (executor.isEmpty()) {
            // A known language with no executor yet is a product state, not a client error:
            // answer with a trace the UI can render as "coming soon".
            return new ExecutionResponse(null, ExecutionTrace.failure(language.name(),
                    ExecutionStatus.UNSUPPORTED_LANGUAGE,
                    language.displayName() + " is not supported yet. Java is available today; "
                            + "Python and C++ are on the roadmap.",
                    null, 0), 0, null);
        }

        int cost = credits.config().costExecution();
        assertAffordable(userId, cost);

        ExecutionTrace trace = runGuarded(executor.get(),
                new ExecutionRequest(language, request.code()));

        int creditsSpent = chargeIfExecuted(userId, trace, cost);
        Integer balance = userId == null ? null : credits.getBalance(userId);
        Long executionId = userId == null ? null
                : record(userId, request, language, trace, creditsSpent);

        return new ExecutionResponse(executionId, trace, creditsSpent, balance);
    }

    /**
     * The {@link CodeExecutor} contract says an executor never throws for bad user input, but a
     * bug in one must not become a 500 that loses the user's work -- so anything that does
     * escape is turned into a trace here as well.
     */
    private ExecutionTrace runGuarded(CodeExecutor executor, ExecutionRequest request) {
        try {
            return sandbox.run(() -> executor.execute(request), limits.wallClockTimeoutMs());
        } catch (ExecutionSandbox.SandboxTimeoutException e) {
            return ExecutionTrace.failure(request.language().name(), ExecutionStatus.TIMEOUT,
                    e.getMessage(), null, limits.wallClockTimeoutMs());
        } catch (RuntimeException e) {
            log.error("Execution failed outside the executor's own error handling", e);
            return ExecutionTrace.failure(request.language().name(),
                    ExecutionStatus.INTERNAL_ERROR,
                    "AlgoLens could not finish running this code.", null, 0);
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

    /**
     * Charges only for code that actually ran. A syntax error costs nothing: charging for a typo
     * teaches people to stop pressing Run, which is the opposite of what this product wants.
     */
    private int chargeIfExecuted(Long userId, ExecutionTrace trace, int cost) {
        if (userId == null || cost <= 0
                || trace.status() == ExecutionStatus.COMPILE_ERROR
                || trace.status() == ExecutionStatus.UNSUPPORTED_LANGUAGE) {
            return 0;
        }
        credits.consume(userId, cost, CreditTransactionType.CODE_EXECUTION,
                "Ran " + trace.language() + " code (" + trace.totalSteps() + " steps)");
        return cost;
    }

    /**
     * Recording is best effort. A run that succeeded and is already on its way to the screen must
     * not turn into an error because the history write failed.
     */
    private Long record(Long userId, ExecuteCodeRequest request, Language language,
            ExecutionTrace trace, int creditsSpent) {
        try {
            return recorder.record(userId, request, language, trace, creditsSpent);
        } catch (RuntimeException e) {
            log.error("Could not record execution history for user {}", userId, e);
            return null;
        }
    }
}
