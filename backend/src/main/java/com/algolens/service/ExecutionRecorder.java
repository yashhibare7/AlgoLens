package com.algolens.service;

import com.algolens.config.AppProperties;
import com.algolens.dto.execution.ExecuteCodeRequest;
import com.algolens.entity.ExecutionHistory;
import com.algolens.entity.Language;
import com.algolens.entity.User;
import com.algolens.exception.NotFoundException;
import com.algolens.repository.ExecutionHistoryRepository;
import com.algolens.repository.SavedCodeRepository;
import com.algolens.repository.UserRepository;
import com.algolens.trace.ExecutionTrace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a finished run to history.
 *
 * <p>A separate bean from {@link ExecutionService} on purpose: {@code @Transactional} is applied
 * by a proxy, so a self-invoked method inside {@code ExecutionService} would silently run with
 * no transaction at all. Crossing a bean boundary is what makes the annotation take effect.
 */
@Service
public class ExecutionRecorder {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRecorder.class);
    private static final int MAX_ERROR_MESSAGE = 4000;

    private final ExecutionHistoryRepository history;
    private final UserRepository users;
    private final SavedCodeRepository savedCode;
    private final ProblemService problems;
    private final ObjectMapper objectMapper;
    private final AppProperties.Execution limits;

    public ExecutionRecorder(ExecutionHistoryRepository history, UserRepository users,
            SavedCodeRepository savedCode, ProblemService problems, ObjectMapper objectMapper,
            AppProperties properties) {
        this.history = history;
        this.users = users;
        this.savedCode = savedCode;
        this.problems = problems;
        this.objectMapper = objectMapper;
        this.limits = properties.execution();
    }

    @Transactional
    public Long record(Long userId, ExecuteCodeRequest request, Language language,
            ExecutionTrace trace, int creditsSpent) {
        User user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));

        ExecutionHistory entity = new ExecutionHistory(user, language, request.code(),
                trace.status());
        entity.setErrorMessage(truncate(trace.errorMessage()));
        entity.setTotalSteps(trace.totalSteps());
        entity.setDurationMs(trace.durationMs());
        entity.setCreditsSpent(creditsSpent);
        entity.setTraceJson(serialize(trace));

        if (request.savedCodeId() != null) {
            savedCode.findByIdAndUserId(request.savedCodeId(), userId)
                    .ifPresent(entity::setSavedCode);
        }
        if (request.problemId() != null) {
            entity.setProblem(problems.requireById(request.problemId()));
        }
        return history.save(entity).getId();
    }

    /**
     * A trace is stored so history can replay a run without re-executing it. Very large traces
     * are dropped rather than bloating the row: the run still appears in history, it just is not
     * replayable, and {@code ExecutionSummaryResponse.replayable} tells the UI which it is.
     */
    private String serialize(ExecutionTrace trace) {
        try {
            String json = objectMapper.writeValueAsString(trace);
            int bytes = json.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > limits.maxPersistedTraceBytes()) {
                log.info("Trace of {} bytes exceeds the {} byte storage limit; not persisting it",
                        bytes, limits.maxPersistedTraceBytes());
                return null;
            }
            return json;
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise a trace for storage: {}", e.getMessage());
            return null;
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE - 3) + "...";
    }
}
