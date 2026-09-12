package com.algolens.service.ai;

import com.algolens.config.AppProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Explanations from Claude, via the official Anthropic Java SDK.
 *
 * <p>The API key is read from server configuration and never leaves the server -- the browser
 * talks only to {@code POST /api/ai/explain}, so there is no path by which a key could end up in
 * a client bundle.
 *
 * <p>Thinking is left at the model's default (adaptive on Claude Opus 5) and {@code effort} is
 * configurable, defaulting to MEDIUM: explaining one step from a trace that is already in the
 * prompt does not need the model's deepest reasoning, and this endpoint sits in front of a user
 * waiting for a panel to fill in. The system prompt deliberately carries no {@code cache_control}
 * breakpoint -- it is well under the minimum cacheable prefix, so a breakpoint there would be a
 * no-op that only looked like an optimisation.
 */
@Component
public class ClaudeExplanationProvider implements ExplanationProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeExplanationProvider.class);

    private final AppProperties.Ai config;
    private volatile AnthropicClient client;

    public ClaudeExplanationProvider(AppProperties properties) {
        this.config = properties.ai();
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public boolean isAvailable() {
        return config.enabled() && config.hasApiKey();
    }

    @Override
    public String explain(ExplanationPrompt prompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .outputConfig(OutputConfig.builder().effort(effort()).build())
                .system(PromptTemplates.SYSTEM)
                .addUserMessage(PromptTemplates.userMessage(prompt))
                .build();
        try {
            return textOf(client().messages().create(params));
        } catch (AnthropicException e) {
            // The provider's own message can carry request details; log it, and give the user
            // something useful without leaking internals.
            log.warn("Anthropic API call failed: {}", e.getMessage());
            throw new AiUnavailableException(
                    "The explanation service is unavailable right now. Please try again.");
        }
    }

    private static String textOf(Message response) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(paragraph -> text.append(paragraph.text()));
        }
        String explanation = text.toString().strip();
        if (explanation.isEmpty()) {
            throw new AiUnavailableException("The model returned an empty explanation.");
        }
        return explanation;
    }

    private OutputConfig.Effort effort() {
        String configured = config.effort() == null ? "medium" : config.effort().trim();
        return switch (configured.toUpperCase()) {
            case "LOW" -> OutputConfig.Effort.LOW;
            case "HIGH" -> OutputConfig.Effort.HIGH;
            case "XHIGH" -> OutputConfig.Effort.XHIGH;
            case "MAX" -> OutputConfig.Effort.MAX;
            case "MEDIUM" -> OutputConfig.Effort.MEDIUM;
            default -> {
                log.warn("Unknown algolens.ai.effort '{}', using MEDIUM", configured);
                yield OutputConfig.Effort.MEDIUM;
            }
        };
    }

    /**
     * Built on first use, so a deployment without a key still starts. The request timeout is set
     * here because the SDK's default is minutes long -- far too long for a panel someone is
     * watching fill in.
     */
    private AnthropicClient client() {
        AnthropicClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                if (!config.hasApiKey()) {
                    throw new AiUnavailableException("No Anthropic API key is configured.");
                }
                client = AnthropicOkHttpClient.builder()
                        .apiKey(config.apiKey())
                        .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                        .build();
                log.info("Anthropic client initialised for model {}", config.model());
            }
            return client;
        }
    }
}
