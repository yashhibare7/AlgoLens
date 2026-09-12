package com.algolens.judge;

import com.algolens.entity.Verdict;
import java.util.List;

public record JudgeResult(
        Verdict verdict,
        int passedCount,
        int totalCount,
        List<TestCaseOutcome> outcomes,
        String errorMessage,
        long durationMs) {
}
