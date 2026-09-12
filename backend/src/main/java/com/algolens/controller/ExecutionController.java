package com.algolens.controller;

import com.algolens.dto.PageResponse;
import com.algolens.dto.execution.ExecuteCodeRequest;
import com.algolens.dto.execution.ExecutionDetailResponse;
import com.algolens.dto.execution.ExecutionResponse;
import com.algolens.dto.execution.ExecutionSummaryResponse;
import com.algolens.security.AuthUser;
import com.algolens.service.ExecutionService;
import com.algolens.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Running code, and reading back runs that already happened.
 *
 * <p>{@code POST} tolerates an anonymous caller so the visualizer works before anyone signs up;
 * every other method here is scoped to the signed-in user's own history.
 */
@RestController
@RequestMapping("/api/executions")
@Tag(name = "Executions")
public class ExecutionController {

    private final ExecutionService executionService;
    private final HistoryService historyService;

    public ExecutionController(ExecutionService executionService, HistoryService historyService) {
        this.executionService = executionService;
        this.historyService = historyService;
    }

    @PostMapping
    @Operation(summary = "Run code and get the step-by-step execution trace",
            description = "Works without authentication when anonymous execution is enabled; "
                    + "signed-in runs are recorded to history and metered against credits.")
    public ExecutionResponse run(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody ExecuteCodeRequest request) {
        return executionService.execute(user == null ? null : user.id(), request);
    }

    @GetMapping
    @Operation(summary = "List my past runs")
    public PageResponse<ExecutionSummaryResponse> history(@AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return historyService.list(user.id(), Pagination.of(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Replay one past run, including its stored trace")
    public ExecutionDetailResponse detail(@AuthenticationPrincipal AuthUser user,
            @PathVariable Long id) {
        return historyService.get(user.id(), id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one past run")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user,
            @PathVariable Long id) {
        historyService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
