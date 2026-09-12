package com.algolens.dto.problem;

import com.algolens.entity.Problem;
import java.util.List;

/**
 * @param starterCode      present on the detail view only
 * @param solutionCode     present on the detail view only, and only when the caller asked for it
 * @param judgeEnabled     true when this problem can be submitted for automated pass/fail judging
 * @param functionSignature the method a submission must implement, e.g.
 *                          {@code int[] twoSum(int[] nums, int target)}; null unless judgeEnabled
 * @param sampleTestCases  visible examples; present on the detail view only, empty when not
 *                         judge-enabled
 */
public record ProblemResponse(
        Long id,
        String slug,
        String title,
        String description,
        String difficulty,
        String category,
        String language,
        String starterCode,
        String solutionCode,
        boolean judgeEnabled,
        String functionSignature,
        List<TestCaseSampleResponse> sampleTestCases) {

    public static ProblemResponse summary(Problem problem) {
        return new ProblemResponse(problem.getId(), problem.getSlug(), problem.getTitle(), null,
                problem.getDifficulty().name(), problem.getCategory(),
                problem.getLanguage().name(), null, null,
                problem.isJudgeEnabled(), problem.functionSignature(), List.of());
    }

    public static ProblemResponse detail(Problem problem, boolean includeSolution,
            List<TestCaseSampleResponse> sampleTestCases) {
        return new ProblemResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getDescription(), problem.getDifficulty().name(), problem.getCategory(),
                problem.getLanguage().name(), problem.getStarterCode(),
                includeSolution ? problem.getSolutionCode() : null,
                problem.isJudgeEnabled(), problem.functionSignature(), sampleTestCases);
    }
}
