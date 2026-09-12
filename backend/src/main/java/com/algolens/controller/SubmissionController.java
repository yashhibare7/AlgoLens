package com.algolens.controller;

import com.algolens.dto.judge.SubmissionResponse;
import com.algolens.dto.judge.SubmissionSummaryResponse;
import com.algolens.dto.judge.SubmitRequest;
import com.algolens.judge.JudgeService;
import com.algolens.repository.SubmissionRepository;
import com.algolens.security.AuthUser;
import com.algolens.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Judging a solution against a problem's test cases.
 *
 * <p>{@code POST .../submit} tolerates an anonymous caller, same as {@link ExecutionController};
 * the history endpoint is scoped to the signed-in user's own submissions for that problem.
 */
@RestController
@RequestMapping("/api/problems/{slug}")
@Tag(name = "Submissions")
public class SubmissionController {

    private final JudgeService judgeService;
    private final ProblemService problemService;
    private final SubmissionRepository submissions;

    public SubmissionController(JudgeService judgeService, ProblemService problemService,
            SubmissionRepository submissions) {
        this.judgeService = judgeService;
        this.problemService = problemService;
        this.submissions = submissions;
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit a solution and judge it against the problem's test cases",
            description = "Works without authentication when anonymous execution is enabled; "
                    + "signed-in submissions are recorded to history and metered against credits.")
    public SubmissionResponse submit(@AuthenticationPrincipal AuthUser user,
            @PathVariable String slug, @Valid @RequestBody SubmitRequest request) {
        return judgeService.submit(user == null ? null : user.id(), slug, request.code());
    }

    @GetMapping("/submissions")
    @Operation(summary = "List my past submissions for this problem")
    public List<SubmissionSummaryResponse> history(@AuthenticationPrincipal AuthUser user,
            @PathVariable String slug) {
        Long problemId = problemService.find(slug).getId();
        return submissions.findByUserIdAndProblemIdOrderByCreatedAtDesc(user.id(), problemId)
                .stream().map(SubmissionSummaryResponse::from).toList();
    }
}
