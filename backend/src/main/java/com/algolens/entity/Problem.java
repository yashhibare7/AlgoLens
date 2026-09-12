package com.algolens.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    // ------------------------------------------------------------------ judge (all nullable)
    //
    // A problem supports automated pass/fail judging only when methodName is set. The existing
    // "run and watch" problems above leave these null: judging is opt-in per problem, not a
    // replacement for the step visualizer.

    @Column(name = "class_name", length = 64)
    private String className;

    @Column(name = "method_name", length = 64)
    private String methodName;

    @Column(name = "method_static", nullable = false)
    private boolean methodStatic;

    @Column(name = "return_type", length = 64)
    private String returnType;

    // Eager: small, and always needed whenever a judge-enabled problem is loaded -- lazy would
    // mean a LazyInitializationException the moment a DTO built from this crosses the
    // transaction boundary, since `open-in-view` is off.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "problem_parameters", joinColumns = @JoinColumn(name = "problem_id"))
    @OrderColumn(name = "position")
    private List<ParamSpec> parameters = new ArrayList<>();

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

    /**
     * Declares the method a submission must implement, turning on the test-case judge for this
     * problem. Returns {@code this} so it chains onto the constructor call in seed data.
     */
    public Problem withJudge(String className, String methodName, boolean methodStatic,
            String returnType, List<ParamSpec> parameters) {
        this.className = className;
        this.methodName = methodName;
        this.methodStatic = methodStatic;
        this.returnType = returnType;
        this.parameters = new ArrayList<>(parameters);
        return this;
    }

    public boolean isJudgeEnabled() {
        return methodName != null;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isMethodStatic() {
        return methodStatic;
    }

    public String getReturnType() {
        return returnType;
    }

    /** A snapshot copy -- see {@code TestCase.getArguments} for why this must not be a view. */
    public List<ParamSpec> getParameters() {
        return List.copyOf(parameters);
    }

    /** A human-readable signature for display, e.g. {@code int[] twoSum(int[] nums, int target)}. */
    public String functionSignature() {
        if (!isJudgeEnabled()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(returnType).append(' ').append(methodName).append('(');
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            ParamSpec p = parameters.get(i);
            sb.append(p.type()).append(' ').append(p.name());
        }
        return sb.append(')').toString();
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
