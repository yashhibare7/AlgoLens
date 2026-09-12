package com.algolens.service;

import com.algolens.dto.problem.ProblemResponse;
import com.algolens.dto.problem.TestCaseSampleResponse;
import com.algolens.entity.Problem;
import com.algolens.exception.NotFoundException;
import com.algolens.repository.ProblemRepository;
import com.algolens.repository.TestCaseRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemService {

    private final ProblemRepository problems;
    private final TestCaseRepository testCases;

    public ProblemService(ProblemRepository problems, TestCaseRepository testCases) {
        this.problems = problems;
        this.testCases = testCases;
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> list(String category) {
        List<Problem> found = category == null || category.isBlank()
                ? problems.findAllByOrderByDisplayOrderAsc()
                : problems.findAllByCategoryIgnoreCaseOrderByDisplayOrderAsc(category.trim());
        return found.stream().map(ProblemResponse::summary).toList();
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(String slugOrId, boolean includeSolution) {
        Problem problem = find(slugOrId);
        List<TestCaseSampleResponse> samples = problem.isJudgeEnabled()
                ? testCases.findByProblemIdAndSampleTrueOrderByDisplayOrderAsc(problem.getId())
                        .stream().map(TestCaseSampleResponse::from).toList()
                : List.of();
        return ProblemResponse.detail(problem, includeSolution, samples);
    }

    @Transactional(readOnly = true)
    public Problem find(String slugOrId) {
        // Accepts either form so links can be readable ("/problems/bubble-sort") while internal
        // references stay numeric.
        return problems.findBySlug(slugOrId)
                .or(() -> parseId(slugOrId).flatMap(problems::findById))
                .orElseThrow(() -> NotFoundException.of("Problem", slugOrId));
    }

    @Transactional(readOnly = true)
    public Problem requireById(Long id) {
        return problems.findById(id).orElseThrow(() -> NotFoundException.of("Problem", id));
    }

    private static java.util.Optional<Long> parseId(String raw) {
        try {
            return java.util.Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
