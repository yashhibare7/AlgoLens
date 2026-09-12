package com.algolens.dto;

import com.algolens.dto.auth.UserResponse;
import com.algolens.dto.execution.ExecutionSummaryResponse;
import java.util.List;

public record DashboardResponse(
        UserResponse user,
        long totalExecutions,
        long successfulExecutions,
        long problemsSolved,
        long savedSnippets,
        int creditBalance,
        List<ExecutionSummaryResponse> recentExecutions) {
}
