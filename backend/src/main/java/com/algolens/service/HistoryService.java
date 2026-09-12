package com.algolens.service;

import com.algolens.dto.PageResponse;
import com.algolens.dto.execution.ExecutionDetailResponse;
import com.algolens.dto.execution.ExecutionSummaryResponse;
import com.algolens.entity.ExecutionHistory;
import com.algolens.exception.NotFoundException;
import com.algolens.repository.ExecutionHistoryRepository;
import com.algolens.trace.ExecutionTrace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

    private final ExecutionHistoryRepository history;
    private final ObjectMapper objectMapper;

    public HistoryService(ExecutionHistoryRepository history, ObjectMapper objectMapper) {
        this.history = history;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionSummaryResponse> list(Long userId, Pageable pageable) {
        return PageResponse.of(history.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                ExecutionSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public ExecutionDetailResponse get(Long userId, Long id) {
        ExecutionHistory entity = history.findByIdAndUserId(id, userId)
                .orElseThrow(() -> NotFoundException.of("Execution", id));
        return new ExecutionDetailResponse(
                entity.getId(),
                entity.getLanguage().name(),
                entity.getStatus().name(),
                entity.getCode(),
                entity.getErrorMessage(),
                entity.getTotalSteps(),
                entity.getDurationMs(),
                entity.getCreditsSpent(),
                deserialize(entity),
                entity.getCreatedAt());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        history.delete(history.findByIdAndUserId(id, userId)
                .orElseThrow(() -> NotFoundException.of("Execution", id)));
    }

    /**
     * A stored trace that no longer parses is a schema-evolution problem, not a reason to fail
     * the request: the run's code and metadata are still worth showing, so the detail view
     * degrades to "not replayable" instead of erroring.
     */
    private ExecutionTrace deserialize(ExecutionHistory entity) {
        if (entity.getTraceJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(entity.getTraceJson(), ExecutionTrace.class);
        } catch (JsonProcessingException e) {
            log.warn("Stored trace for execution {} could not be read back: {}", entity.getId(),
                    e.getMessage());
            return null;
        }
    }
}
