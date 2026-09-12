package com.algolens.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One recorded run. {@code traceJson} holds the serialised {@code ExecutionTrace} so a run can
 * be replayed later without re-executing the code, which is what makes the history page cheap.
 * It is left null when a trace is larger than
 * {@code algolens.execution.max-persisted-trace-bytes}.
 */
@Entity
@Table(name = "execution_history")
@EntityListeners(AuditingEntityListener.class)
public class ExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_code_id")
    private SavedCode savedCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Language language;

    @Column(nullable = false, length = 100000)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExecutionStatus status;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "total_steps", nullable = false)
    private int totalSteps;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "credits_spent", nullable = false)
    private int creditsSpent;

    @Column(name = "trace_json", length = 4_000_000)
    private String traceJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExecutionHistory() {
        // for JPA
    }

    public ExecutionHistory(User user, Language language, String code, ExecutionStatus status) {
        this.user = user;
        this.language = language;
        this.code = code;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public SavedCode getSavedCode() {
        return savedCode;
    }

    public void setSavedCode(SavedCode savedCode) {
        this.savedCode = savedCode;
    }

    public Problem getProblem() {
        return problem;
    }

    public void setProblem(Problem problem) {
        this.problem = problem;
    }

    public Language getLanguage() {
        return language;
    }

    public String getCode() {
        return code;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = totalSteps;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getCreditsSpent() {
        return creditsSpent;
    }

    public void setCreditsSpent(int creditsSpent) {
        this.creditsSpent = creditsSpent;
    }

    public String getTraceJson() {
        return traceJson;
    }

    public void setTraceJson(String traceJson) {
        this.traceJson = traceJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
