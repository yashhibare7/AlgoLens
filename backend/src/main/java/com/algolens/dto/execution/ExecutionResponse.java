package com.algolens.dto.execution;

import com.algolens.trace.ExecutionTrace;

/**
 * @param executionId   history row id, or null for an anonymous run that was not persisted
 * @param trace         the whole point: every step the frontend animates
 * @param creditsSpent  what this run cost
 * @param creditBalance remaining balance, or null when not signed in
 */
public record ExecutionResponse(
        Long executionId,
        ExecutionTrace trace,
        int creditsSpent,
        Integer creditBalance) {
}
