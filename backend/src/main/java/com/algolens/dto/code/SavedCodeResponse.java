package com.algolens.dto.code;

import com.algolens.entity.SavedCode;
import java.time.Instant;

public record SavedCodeResponse(
        Long id,
        String title,
        String language,
        String code,
        Long problemId,
        String problemTitle,
        Instant createdAt,
        Instant updatedAt) {

    public static SavedCodeResponse from(SavedCode saved) {
        return new SavedCodeResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getLanguage().name(),
                saved.getCode(),
                saved.getProblem() == null ? null : saved.getProblem().getId(),
                saved.getProblem() == null ? null : saved.getProblem().getTitle(),
                saved.getCreatedAt(),
                saved.getUpdatedAt());
    }

    /** List view: same shape without the body, which can be 100 KB. */
    public static SavedCodeResponse summary(SavedCode saved) {
        return new SavedCodeResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getLanguage().name(),
                null,
                saved.getProblem() == null ? null : saved.getProblem().getId(),
                saved.getProblem() == null ? null : saved.getProblem().getTitle(),
                saved.getCreatedAt(),
                saved.getUpdatedAt());
    }
}
