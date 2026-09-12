package com.algolens.service;

import com.algolens.config.AppProperties;
import com.algolens.dto.MetaResponse;
import com.algolens.entity.Language;
import com.algolens.execution.ExecutorRegistry;
import com.algolens.service.ai.AiExplanationService;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Serves the frontend its capability list at boot.
 *
 * <p>The UI reads limits, prices, which languages have executors and whether AI is configured
 * from here instead of hardcoding them. So the language picker greys out what has no executor,
 * the editor enforces the same character limit the backend does, and the AI panel labels itself
 * honestly -- all without a frontend release when the backend changes.
 */
@Service
public class MetaService {

    private final ExecutorRegistry executors;
    private final AiExplanationService ai;
    private final CreditService credits;
    private final AppProperties properties;

    public MetaService(ExecutorRegistry executors, AiExplanationService ai, CreditService credits,
            AppProperties properties) {
        this.executors = executors;
        this.ai = ai;
        this.credits = credits;
        this.properties = properties;
    }

    public MetaResponse describe() {
        Set<Language> supported = executors.supportedLanguages();
        List<MetaResponse.LanguageInfo> languages = Arrays.stream(Language.values())
                .map(language -> new MetaResponse.LanguageInfo(language.name(),
                        language.displayName(), supported.contains(language)))
                .toList();

        AppProperties.Execution execution = properties.execution();
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxCodeLength", execution.maxCodeLength());
        limits.put("maxSteps", execution.maxSteps());
        limits.put("maxTraceEvents", execution.maxTraceEvents());
        limits.put("wallClockTimeoutMs", execution.wallClockTimeoutMs());
        limits.put("maxArrayLength", execution.maxArrayLength());
        limits.put("maxCallDepth", execution.maxCallDepth());

        return new MetaResponse(
                "0.1.0",
                languages,
                limits,
                credits.priceList(),
                credits.config().enforced(),
                properties.ai().enabled(),
                ai.activeProviderName(),
                execution.allowAnonymous());
    }
}
