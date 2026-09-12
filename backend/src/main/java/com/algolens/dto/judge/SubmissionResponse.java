package com.algolens.dto.judge;

import com.algolens.judge.JudgeResult;

/**
 * @param submissionId  history row id, or null for an anonymous submission that was not persisted
 * @param result        the verdict and per-test outcomes
 * @param creditsSpent  what this submission cost
 * @param creditBalance remaining balance, or null when not signed in
 */
public record SubmissionResponse(
        Long submissionId,
        JudgeResult result,
        int creditsSpent,
        Integer creditBalance) {
}
