package com.algolens.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable knob in one place. Bound from the {@code algolens.*} section of
 * application.yml, which in turn reads environment variables so nothing secret
 * has to live in the repository.
 */
@ConfigurationProperties(prefix = "algolens")
public record AppProperties(
        Cors cors,
        Jwt jwt,
        Execution execution,
        Credits credits,
        Ai ai) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(String secret, String issuer, long expiryMinutes) {
    }

    /**
     * Hard limits applied to every user submission. These are the only thing standing
     * between the service and a runaway program, so they are enforced by the interpreter
     * itself rather than by convention.
     */
    public record Execution(
            boolean allowAnonymous,
            int maxCodeLength,
            long maxSteps,
            int maxTraceEvents,
            long wallClockTimeoutMs,
            int maxOutputChars,
            int maxCallDepth,
            int maxArrayLength,
            int maxPersistedTraceBytes) {
    }

    public record Credits(
            boolean enforced,
            int signupGrant,
            int monthlyGrant,
            int costExecution,
            int costAiExplanation,
            int costAiAnalysis) {
    }

    public record Ai(
            boolean enabled,
            String apiKey,
            String model,
            long maxTokens,
            String effort,
            long timeoutSeconds) {

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
