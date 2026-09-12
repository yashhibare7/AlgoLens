package com.algolens.dto.ai;

/**
 * @param provider "claude" when the Anthropic API answered, "heuristic" when the deterministic
 *                 local explainer did. Surfaced so the UI can be honest about which one the user
 *                 is reading.
 */
public record ExplainResponse(
        ExplainMode mode,
        String explanation,
        String provider,
        String model,
        int creditsSpent,
        Integer creditBalance) {
}
