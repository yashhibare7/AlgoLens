package com.algolens.trace;

import com.algolens.entity.ExecutionStatus;
import java.util.List;

/**
 * The complete contract between any executor and the frontend.
 *
 * <p>Nothing in here is Java specific. A Python or C++ executor added later produces this exact
 * shape and every visualizer component keeps working unchanged -- that is the whole point of
 * the design.
 *
 * @param language     the language the code was written in
 * @param status       overall outcome
 * @param steps        the trace itself, in execution order
 * @param totalSteps   {@code steps.size()}, echoed for convenience
 * @param truncated    true when the step budget cut the trace short
 * @param stdout       everything the program printed
 * @param errorMessage failure description, or null on success
 * @param errorLine    1-based line the failure occurred on, when known
 * @param durationMs   wall clock time spent interpreting
 * @param metrics      aggregate counters over the run
 */
public record ExecutionTrace(
        String language,
        ExecutionStatus status,
        List<TraceEvent> steps,
        int totalSteps,
        boolean truncated,
        String stdout,
        String errorMessage,
        Integer errorLine,
        long durationMs,
        TraceMetrics metrics) {

    public static ExecutionTrace failure(String language, ExecutionStatus status, String message,
            Integer line, long durationMs) {
        return new ExecutionTrace(language, status, List.of(), 0, false, "", message, line,
                durationMs, TraceMetrics.empty());
    }
}
