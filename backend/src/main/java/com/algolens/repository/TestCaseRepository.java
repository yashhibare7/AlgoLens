package com.algolens.repository;

import com.algolens.entity.TestCase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderByDisplayOrderAsc(Long problemId);

    List<TestCase> findByProblemIdAndSampleTrueOrderByDisplayOrderAsc(Long problemId);
}
