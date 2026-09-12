package com.algolens.repository;

import com.algolens.entity.ExecutionHistory;
import com.algolens.entity.ExecutionStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistory, Long> {

    Page<ExecutionHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<ExecutionHistory> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, ExecutionStatus status);

    @Query("select count(distinct e.problem.id) from ExecutionHistory e "
            + "where e.user.id = :userId and e.problem is not null and e.status = 'SUCCESS'")
    long countDistinctSolvedProblems(@Param("userId") Long userId);
}
