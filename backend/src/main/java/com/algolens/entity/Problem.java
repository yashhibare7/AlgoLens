package com.algolens.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "problems")
@EntityListeners(AuditingEntityListener.class)
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 8000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Difficulty difficulty;

    @Column(nullable = false, length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Language language;

    @Column(name = "starter_code", length = 100000)
    private String starterCode;

    @Column(name = "solution_code", length = 100000)
    private String solutionCode;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Problem() {
        // for JPA
    }

    public Problem(String slug, String title, String description, Difficulty difficulty, String category,
            Language language, String starterCode, String solutionCode, int displayOrder) {
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.difficulty = difficulty;
        this.category = category;
        this.language = language;
        this.starterCode = starterCode;
        this.solutionCode = solutionCode;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getCategory() {
        return category;
    }

    public Language getLanguage() {
        return language;
    }

    public String getStarterCode() {
        return starterCode;
    }

    public String getSolutionCode() {
        return solutionCode;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
