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

/** One judged attempt at a problem: the code, the verdict, and how many test cases passed. */
@Entity
@Table(name = "submissions")
@EntityListeners(AuditingEntityListener.class)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Language language;

    @Column(nullable = false, length = 100000)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Verdict verdict;

    @Column(name = "passed_count", nullable = false)
    private int passedCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Submission() {
        // for JPA
    }

    public Submission(User user, Problem problem, Language language, String code, Verdict verdict,
            int passedCount, int totalCount, String errorMessage, long durationMs) {
        this.user = user;
        this.problem = problem;
        this.language = language;
        this.code = code;
        this.verdict = verdict;
        this.passedCount = passedCount;
        this.totalCount = totalCount;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Problem getProblem() {
        return problem;
    }

    public Language getLanguage() {
        return language;
    }

    public String getCode() {
        return code;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public int getPassedCount() {
        return passedCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
