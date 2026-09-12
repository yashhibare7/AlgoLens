package com.algolens.service;

import com.algolens.dto.DashboardResponse;
import com.algolens.dto.auth.UserResponse;
import com.algolens.dto.execution.ExecutionSummaryResponse;
import com.algolens.entity.ExecutionStatus;
import com.algolens.exception.NotFoundException;
import com.algolens.repository.ExecutionHistoryRepository;
import com.algolens.repository.SavedCodeRepository;
import com.algolens.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int RECENT_LIMIT = 8;

    private final UserRepository users;
    private final ExecutionHistoryRepository history;
    private final SavedCodeRepository savedCode;

    public DashboardService(UserRepository users, ExecutionHistoryRepository history,
            SavedCodeRepository savedCode) {
        this.users = users;
        this.history = history;
        this.savedCode = savedCode;
    }

    @Transactional(readOnly = true)
    public DashboardResponse forUser(Long userId) {
        UserResponse user = users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> NotFoundException.of("User", userId));

        List<ExecutionSummaryResponse> recent = history
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT_LIMIT))
                .map(ExecutionSummaryResponse::from)
                .getContent();

        return new DashboardResponse(
                user,
                history.countByUserId(userId),
                history.countByUserIdAndStatus(userId, ExecutionStatus.SUCCESS),
                history.countDistinctSolvedProblems(userId),
                savedCode.countByUserId(userId),
                user.creditBalance(),
                recent);
    }
}
