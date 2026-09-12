package com.algolens.service.ai;

/**
 * Something that can explain a step or an algorithm.
 *
 * <p>Two implementations ship: {@link ClaudeExplanationProvider}, and
 * {@link HeuristicExplanationProvider} which needs no API key and no network. Keeping the
 * fallback behind the same interface means the feature is never simply missing -- a fresh clone
 * with no credentials still shows explanations, just plainer ones, and
 * {@code ExplainResponse.provider} tells the user which they are reading.
 */
public interface ExplanationProvider {

    /** Short identifier surfaced to the client: "claude" or "heuristic". */
    String name();

    /** Model identifier, or null when the provider is not model based. */
    String model();

    boolean isAvailable();

    String explain(ExplanationPrompt prompt);
}
