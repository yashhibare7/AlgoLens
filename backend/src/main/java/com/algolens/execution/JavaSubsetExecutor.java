package com.algolens.execution;

import com.algolens.config.AppProperties;
import com.algolens.entity.ExecutionStatus;
import com.algolens.entity.Language;
import com.algolens.execution.interpreter.Ast;
import com.algolens.execution.interpreter.BudgetExceededException;
import com.algolens.execution.interpreter.ExecutionBudget;
import com.algolens.execution.interpreter.Interpreter;
import com.algolens.execution.interpreter.InterpreterException;
import com.algolens.execution.interpreter.Parser;
import com.algolens.execution.interpreter.SyntaxException;
import com.algolens.trace.ExecutionTrace;
import com.algolens.trace.TraceBuilder;
import com.algolens.trace.TraceLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The Java executor: parses the supported subset and interprets it, recording a trace.
 *
 * <p>Its whole job is turning every possible failure into a trace with a status and a message
 * the user can act on, per the {@link CodeExecutor} contract. A partial trace is still returned
 * for runtime errors and limit overruns, because the steps leading up to the failure are exactly
 * what the user needs in order to understand it.
 */
@Component
public class JavaSubsetExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(JavaSubsetExecutor.class);
    private static final String LANGUAGE = "JAVA";

    private final AppProperties.Execution limits;

    public JavaSubsetExecutor(AppProperties properties) {
        this.limits = properties.execution();
    }

    @Override
    public Language language() {
        return Language.JAVA;
    }

    @Override
    public ExecutionTrace execute(ExecutionRequest request) {
        long startedAt = System.nanoTime();
        TraceBuilder trace = new TraceBuilder(limits.maxTraceEvents(), limits.maxOutputChars());
        Interpreter interpreter = null;

        try {
            Ast.Program program = Parser.parse(request.code());
            interpreter = new Interpreter(program, trace, new ExecutionBudget(
                    limits.maxSteps(),
                    limits.wallClockTimeoutMs(),
                    limits.maxCallDepth(),
                    limits.maxArrayLength()));
            interpreter.run();
            return trace.build(LANGUAGE, ExecutionStatus.SUCCESS, null, null, elapsedMs(startedAt));

        } catch (SyntaxException e) {
            // Nothing ran, so there is no partial trace worth returning.
            return ExecutionTrace.failure(LANGUAGE, ExecutionStatus.COMPILE_ERROR, e.getMessage(),
                    e.line() > 0 ? e.line() : null, elapsedMs(startedAt));

        } catch (InterpreterException e) {
            return trace.build(LANGUAGE, ExecutionStatus.RUNTIME_ERROR, e.getMessage(),
                    e.line() > 0 ? e.line() : null, elapsedMs(startedAt));

        } catch (BudgetExceededException e) {
            trace.markTruncated();
            return trace.build(LANGUAGE, e.status(), e.getMessage(), lineOf(interpreter),
                    elapsedMs(startedAt));

        } catch (TraceLimitExceededException e) {
            trace.markTruncated();
            return trace.build(LANGUAGE, ExecutionStatus.LIMIT_EXCEEDED, e.getMessage(),
                    lineOf(interpreter), elapsedMs(startedAt));

        } catch (StackOverflowError e) {
            // Deep recursion or pathologically nested expressions. Not an internal error: the
            // submission asked for more stack than it is allowed.
            trace.markTruncated();
            return trace.build(LANGUAGE, ExecutionStatus.LIMIT_EXCEEDED,
                    "Recursion or expression nesting went too deep", lineOf(interpreter),
                    elapsedMs(startedAt));

        } catch (RuntimeException e) {
            // A bug in the executor, not in the submission. Log it with the code so it can be
            // reproduced, and tell the user something honest.
            log.error("Executor failed unexpectedly for a {}-character submission",
                    request.code().length(), e);
            return trace.build(LANGUAGE, ExecutionStatus.INTERNAL_ERROR,
                    "AlgoLens hit an internal error while running this code. "
                            + "The submission has been logged so it can be fixed.",
                    lineOf(interpreter), elapsedMs(startedAt));
        }
    }

    private static Integer lineOf(Interpreter interpreter) {
        if (interpreter == null) {
            return null;
        }
        int line = interpreter.currentLine();
        return line > 0 ? line : null;
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
