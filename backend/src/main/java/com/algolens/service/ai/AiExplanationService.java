package com.algolens.service.ai;

import com.algolens.config.AppProperties;
import com.algolens.dto.ai.ExplainMode;
import com.algolens.dto.ai.ExplainRequest;
import com.algolens.dto.ai.ExplainResponse;
import com.algolens.entity.CreditTransactionType;
import com.algolens.exception.InsufficientCreditsException;
import com.algolens.service.CreditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Picks a provider, meters the request, and never leaves the user with a dead button.
 *
 * <p>Billing rule: <b>only a real model answer costs credits.</b> If Claude is unreachable and
 * the local heuristic answers instead, the request is free. Charging for the fallback would be
 * charging for our outage.
 */
@Service
public class AiExplanationService {

    private static final Logger log = LoggerFactory.getLogger(AiExplanationService.class);

    private final ClaudeExplanationProvider claude;
    private final HeuristicExplanationProvider heuristic;
    private final CreditService credits;
    private final AppProperties.Ai config;

    public AiExplanationService(ClaudeExplanationProvider claude,
            HeuristicExplanationProvider heuristic, CreditService credits,
            AppProperties properties) {
        this.claude = claude;
        this.heuristic = heuristic;
        this.credits = credits;
        this.config = properties.ai();
    }

    public boolean isModelBacked() {
        return claude.isAvailable();
    }

    public String activeProviderName() {
        return claude.isAvailable() ? claude.name() : heuristic.name();
    }

    public ExplainResponse explain(Long userId, ExplainRequest request) {
        ExplanationPrompt prompt = new ExplanationPrompt(request.mode(),
                request.language() == null ? "Java" : request.language(), request.code(),
                request.stepIndex(), request.traceExcerpt(), request.question());

        int cost = costOf(request.mode());
        assertAffordable(userId, cost);

        if (!claude.isAvailable()) {
            // No key configured: answer locally, free of charge, and say so in the response.
            return new ExplainResponse(request.mode(), heuristic.explain(prompt),
                    heuristic.name(), null, 0, credits.getBalance(userId));
        }

        try {
            String explanation = claude.explain(prompt);
            credits.consume(userId, cost, transactionTypeOf(request.mode()),
                    "AI " + request.mode() + " (" + config.model() + ")");
            return new ExplainResponse(request.mode(), explanation, claude.name(), claude.model(),
                    cost, credits.getBalance(userId));

        } catch (AiUnavailableException e) {
            log.warn("Falling back to the local explainer: {}", e.getMessage());
            String explanation = heuristic.explain(prompt);
            return new ExplainResponse(request.mode(), explanation, heuristic.name(), null, 0,
                    credits.getBalance(userId));
        }
    }

    private void assertAffordable(Long userId, int cost) {
        if (!credits.config().enforced() || cost <= 0 || !claude.isAvailable()) {
            return;
        }
        int balance = credits.getBalance(userId);
        if (balance < cost) {
            throw new InsufficientCreditsException(cost, balance);
        }
    }

    private int costOf(ExplainMode mode) {
        return mode == ExplainMode.FIND_BUG
                ? credits.config().costAiAnalysis()
                : credits.config().costAiExplanation();
    }

    private static CreditTransactionType transactionTypeOf(ExplainMode mode) {
        return mode == ExplainMode.FIND_BUG
                ? CreditTransactionType.AI_ANALYSIS
                : CreditTransactionType.AI_EXPLANATION;
    }
}
