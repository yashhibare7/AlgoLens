package com.algolens.controller;

import com.algolens.dto.problem.ProblemResponse;
import com.algolens.security.AuthUser;
import com.algolens.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The starter problem library. Readable without an account so the landing page has content. */
@RestController
@RequestMapping("/api/problems")
@Tag(name = "Problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    @Operation(summary = "List problems, optionally filtered by category")
    public List<ProblemResponse> list(@RequestParam(required = false) String category) {
        return problemService.list(category);
    }

    @GetMapping("/{slugOrId}")
    @Operation(summary = "One problem, with its starter code",
            description = "The reference solution is only included for signed-in users, so the "
                    + "public landing page cannot be scraped for answers.")
    public ProblemResponse get(@PathVariable String slugOrId,
            @RequestParam(defaultValue = "false") boolean includeSolution,
            @AuthenticationPrincipal AuthUser user) {
        return problemService.get(slugOrId, includeSolution && user != null);
    }
}
