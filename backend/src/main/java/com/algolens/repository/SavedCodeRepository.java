package com.algolens.repository;

import com.algolens.entity.SavedCode;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedCodeRepository extends JpaRepository<SavedCode, Long> {

    Page<SavedCode> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    Optional<SavedCode> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
