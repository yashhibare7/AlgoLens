package com.algolens.dto.judge;

import com.algolens.entity.Submission;
import java.time.Instant;

/** One row of a problem's submission history for the signed-in user. */
public record SubmissionSummaryResponse(
        Long id,
        String verdict,
        int passedCount,
        int totalCount,
        long durationMs,
        Instant createdAt) {

    public static SubmissionSummaryResponse from(Submission submission) {
        return new SubmissionSummaryResponse(
                submission.getId(),
                submission.getVerdict().name(),
                submission.getPassedCount(),
                submission.getTotalCount(),
                submission.getDurationMs(),
                submission.getCreatedAt());
    }
}
