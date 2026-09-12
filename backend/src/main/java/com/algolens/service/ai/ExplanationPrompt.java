package com.algolens.service.ai;

import com.algolens.dto.ai.ExplainMode;
import java.util.List;

/**
 * Everything a provider gets. Note what is absent: no user id, no email, nothing about the
 * account. Only the code the user wrote and the trace their own run produced leaves the process.
 */
public record ExplanationPrompt(
        ExplainMode mode,
        String language,
        String code,
        Integer stepIndex,
        List<String> traceExcerpt,
        String question) {
}
