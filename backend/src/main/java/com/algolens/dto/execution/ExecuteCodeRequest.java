package com.algolens.dto.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param language     "JAVA" for now; the field exists so the contract does not change when
 *                     Python and C++ executors land
 * @param code         the submission
 * @param savedCodeId  optional link back to a saved snippet, for the history page
 * @param problemId    optional link back to a library problem, for progress tracking
 */
public record ExecuteCodeRequest(
        @NotBlank(message = "Language is required") String language,

        @NotBlank(message = "Write some code before running it")
        @Size(max = 20000, message = "Code must be at most 20000 characters")
        String code,

        Long savedCodeId,
        Long problemId) {
}
