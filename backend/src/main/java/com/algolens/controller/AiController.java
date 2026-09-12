package com.algolens.controller;

import com.algolens.dto.ai.ExplainRequest;
import com.algolens.dto.ai.ExplainResponse;
import com.algolens.security.AuthUser;
import com.algolens.service.ai.AiExplanationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The AI panel's only endpoint.
 *
 * <p>Requires an account: the model call costs real money, so it is metered per user. The API key
 * lives on the server and the browser never sees it.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI")
public class AiController {

    private final AiExplanationService aiExplanationService;

    public AiController(AiExplanationService aiExplanationService) {
        this.aiExplanationService = aiExplanationService;
    }

    @PostMapping("/explain")
    @Operation(summary = "Explain a step, the algorithm, a suspected bug, or the complexity",
            description = "Falls back to a deterministic local explainer, free of charge, when "
                    + "no model is configured or the provider is unreachable.")
    public ExplainResponse explain(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody ExplainRequest request) {
        return aiExplanationService.explain(user.id(), request);
    }
}
