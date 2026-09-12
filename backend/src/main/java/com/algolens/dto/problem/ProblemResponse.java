package com.algolens.dto.problem;

import com.algolens.entity.Problem;

/**
 * @param starterCode  present on the detail view only
 * @param solutionCode present on the detail view only, and only when the caller asked for it
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
        String solutionCode) {

    public static ProblemResponse summary(Problem problem) {
        return new ProblemResponse(problem.getId(), problem.getSlug(), problem.getTitle(), null,
                problem.getDifficulty().name(), problem.getCategory(),
                problem.getLanguage().name(), null, null);
    }

    public static ProblemResponse detail(Problem problem, boolean includeSolution) {
        return new ProblemResponse(problem.getId(), problem.getSlug(), problem.getTitle(),
                problem.getDescription(), problem.getDifficulty().name(), problem.getCategory(),
                problem.getLanguage().name(), problem.getStarterCode(),
                includeSolution ? problem.getSolutionCode() : null);
    }
}
