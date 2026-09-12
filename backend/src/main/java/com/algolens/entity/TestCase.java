package com.algolens.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One input/expected-output pair for a judge-enabled {@link Problem}.
 *
 * <p>{@code arguments} holds one Java source literal per parameter, in the problem's parameter
 * order (e.g. {@code "{2, 7, 11, 15}"}, {@code "9"}) -- the same convention the interpreter's own
 * {@code EntryPointHelp} uses for sample values, so the judge harness can splice them directly
 * into a variable declaration without any value-to-literal conversion. {@code expectedOutput} is
 * the exact string the interpreter's {@code System.out.println} would produce for the correct
 * return value (arrays render as {@code [1, 2, 3]}, see {@code Values.format}).
 */
@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    // Eager: small, and always needed for judging -- lazy would mean a LazyInitializationException
    // the moment code outside the loading transaction touches it (see JudgeHarnessBuilder, which
    // reads every test case's arguments well after the repository call that loaded them returns).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "test_case_arguments", joinColumns = @JoinColumn(name = "test_case_id"))
    @OrderColumn(name = "position")
    @Column(name = "arg_value", length = 2000)
    private List<String> arguments = new ArrayList<>();

    @Column(name = "expected_output", nullable = false, length = 2000)
    private String expectedOutput;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    @Column(length = 1000)
    private String explanation;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected TestCase() {
        // for JPA
    }

    public TestCase(Problem problem, List<String> arguments, String expectedOutput, boolean sample,
            String explanation, int displayOrder) {
        this.problem = problem;
        this.arguments = new ArrayList<>(arguments);
        this.expectedOutput = expectedOutput;
        this.sample = sample;
        this.explanation = explanation;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Problem getProblem() {
        return problem;
    }

    /**
     * A snapshot copy, not a view: {@code arguments} is a lazy Hibernate-backed collection, and
     * {@code open-in-view: false} means the session is gone by the time a DTO built from this
     * gets serialized. {@link Collections#unmodifiableList} would just wrap the live proxy and
     * defer the failure to Jackson; {@link List#copyOf} forces it to load now, while the
     * transaction is still open.
     */
    public List<String> getArguments() {
        return List.copyOf(arguments);
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public boolean isSample() {
        return sample;
    }

    public String getExplanation() {
        return explanation;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
