package com.algolens.dto.execution;

import com.algolens.trace.ExecutionTrace;
import java.time.Instant;

/**
 * A replayable run: the original code plus the stored trace, so opening it from history steps
 * through the identical execution without running anything again.
 *
 * @param trace null when the run predates trace storage or was too large to persist
 */
public record ExecutionDetailResponse(
        Long id,
        String language,
        String status,
        String code,
        String errorMessage,
        int totalSteps,
        long durationMs,
        int creditsSpent,
        ExecutionTrace trace,
        Instant createdAt) {
}
