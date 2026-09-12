package com.algolens.dto.problem;

import com.algolens.entity.TestCase;
import java.util.List;

/** A sample test case, shown on the problem page before a submission is ever made. */
public record TestCaseSampleResponse(
        List<String> arguments,
        String expectedOutput,
        String explanation) {

    public static TestCaseSampleResponse from(TestCase testCase) {
        return new TestCaseSampleResponse(testCase.getArguments(), testCase.getExpectedOutput(),
                testCase.getExplanation());
    }
}
