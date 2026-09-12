package com.algolens.dto.execution;

import com.algolens.entity.ExecutionHistory;
import java.time.Instant;

/** One row of the history list. Deliberately excludes the code and the trace. */
public record ExecutionSummaryResponse(
        Long id,
        String language,
        String status,
        String title,
        int totalSteps,
        long durationMs,
        int creditsSpent,
        boolean replayable,
        Instant createdAt) {

    public static ExecutionSummaryResponse from(ExecutionHistory history) {
        String title = history.getSavedCode() != null ? history.getSavedCode().getTitle()
                : history.getProblem() != null ? history.getProblem().getTitle()
                        : "Untitled run";
        return new ExecutionSummaryResponse(
                history.getId(),
                history.getLanguage().name(),
                history.getStatus().name(),
                title,
                history.getTotalSteps(),
                history.getDurationMs(),
                history.getCreditsSpent(),
                history.getTraceJson() != null,
                history.getCreatedAt());
    }
}
