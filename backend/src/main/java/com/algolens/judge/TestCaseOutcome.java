package com.algolens.judge;

/**
 * One test case's result. {@code actualOutput}/{@code expectedOutput} are only populated for
 * sample test cases -- hidden ones report pass/fail only, the same way a real judge does not leak
 * its hidden inputs back to a failing submission.
 */
public record TestCaseOutcome(
        int index,
        boolean sample,
        boolean passed,
        String actualOutput,
        String expectedOutput,
        String errorMessage) {
}
