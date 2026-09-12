package com.algolens.dto;

import java.util.List;
import java.util.Map;

/**
 * Capability advertisement. The frontend reads this at boot instead of hardcoding which
 * languages work, what the limits are, or whether AI is configured -- so the UI adapts when the
 * backend gains an executor or has no API key.
 */
public record MetaResponse(
        String version,
        List<LanguageInfo> languages,
        Map<String, Object> limits,
        Map<String, Integer> creditCosts,
        boolean creditsEnforced,
        boolean aiEnabled,
        String aiProvider,
        boolean anonymousExecutionAllowed) {

    public record LanguageInfo(String id, String displayName, boolean supported) {
    }
}
