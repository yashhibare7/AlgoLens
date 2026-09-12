package com.algolens.execution;

import com.algolens.entity.Language;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps a language to its executor. Spring injects every {@link CodeExecutor} bean on the
 * classpath, so registering a new language is a matter of adding a {@code @Component} -- no
 * switch statement anywhere has to learn about it.
 */
@Component
public class ExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExecutorRegistry.class);

    private final Map<Language, CodeExecutor> executors = new EnumMap<>(Language.class);

    public ExecutorRegistry(List<CodeExecutor> available) {
        for (CodeExecutor executor : available) {
            CodeExecutor previous = executors.put(executor.language(), executor);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two executors registered for " + executor.language());
            }
        }
        log.info("Registered executors for: {}", executors.keySet());
    }

    public Optional<CodeExecutor> forLanguage(Language language) {
        return Optional.ofNullable(executors.get(language));
    }

    public Set<Language> supportedLanguages() {
        return Set.copyOf(executors.keySet());
    }
}
