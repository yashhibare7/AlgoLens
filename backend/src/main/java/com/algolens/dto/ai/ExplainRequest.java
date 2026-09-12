package com.algolens.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param traceExcerpt a window of already-rendered step summaries around {@code stepIndex}. The
 *                     frontend sends these instead of the raw trace: it is a fraction of the
 *                     payload, it is what the model actually needs, and it means the assistant
 *                     works for anonymous runs that were never persisted server-side.
 * @param question     optional free-text follow-up
 */
public record ExplainRequest(
        @NotNull(message = "Mode is required") ExplainMode mode,

        String language,

        @NotBlank(message = "There is no code to explain")
        @Size(max = 20000, message = "Code must be at most 20000 characters")
        String code,

        Integer stepIndex,

        @Size(max = 60, message = "Send at most 60 trace lines")
        List<@Size(max = 500) String> traceExcerpt,

        @Size(max = 500, message = "Question must be at most 500 characters")
        String question) {
}
