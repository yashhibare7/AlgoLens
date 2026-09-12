package com.algolens.dto.judge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitRequest(
        @NotBlank(message = "Write some code before submitting it")
        @Size(max = 20000, message = "Code must be at most 20000 characters")
        String code) {
}
