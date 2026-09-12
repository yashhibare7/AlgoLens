package com.algolens.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.algolens.entity.ExecutionStatus;
import com.algolens.entity.Problem;
import com.algolens.execution.TestExecutor;
import com.algolens.trace.ExecutionTrace;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every snippet in the starter library must actually run.
 *
 * <p>Seed data that no longer parses is the worst kind of regression: the product looks broken to
 * a first-time visitor while every unit test still passes. This turns that into a build failure.
 */
class ProblemLibraryTest {

    static List<Problem> library() {
        return ProblemSeeder.library();
    }

    @ParameterizedTest(name = "{0} runs successfully")
    @MethodSource("library")
    @DisplayName("every seeded problem's starter code executes without error")
    void starterCodeRuns(Problem problem) {
        assertThat(problem.getStarterCode())
                .as("problem '%s' has no starter code", problem.getSlug())
                .isNotBlank();

        ExecutionTrace trace = TestExecutor.run(problem.getStarterCode());

        assertThat(trace.status())
                .as("problem '%s' failed: %s (line %s)", problem.getSlug(),
                        trace.errorMessage(), trace.errorLine())
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(trace.steps())
                .as("problem '%s' produced no steps to visualise", problem.getSlug())
                .isNotEmpty();
    }

    @ParameterizedTest(name = "{0} has complete metadata")
    @MethodSource("library")
    void metadataIsComplete(Problem problem) {
        assertThat(problem.getSlug()).matches("[a-z0-9-]+");
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDescription()).isNotBlank();
        assertThat(problem.getCategory()).isNotBlank();
        assertThat(problem.getDifficulty()).isNotNull();
        assertThat(problem.getLanguage()).isNotNull();
    }
}
