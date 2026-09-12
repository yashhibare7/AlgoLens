package com.algolens.dto.code;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SavedCodeRequest(
        @NotBlank(message = "Give this snippet a title")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Language is required") String language,

        @NotBlank(message = "There is no code to save")
        @Size(max = 100000, message = "Code must be at most 100000 characters")
        String code,

        Long problemId) {
}
