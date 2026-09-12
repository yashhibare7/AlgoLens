package com.algolens.judge;

import com.algolens.entity.ParamSpec;
import com.algolens.entity.Problem;
import com.algolens.entity.TestCase;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns a submission into a program the existing Java-subset interpreter can already run.
 *
 * <p>No new execution engine is involved: this appends a synthetic driver class after the user's
 * code, one {@code try/catch} block per test case, each declaring the case's arguments as local
 * variables and printing a marked line with the call's result. {@code JudgeService} then reads
 * those marker lines back out of the trace's {@code stdout}.
 *
 * <p>This mirrors {@code EntryPointHelp}'s existing trick of building a driver {@code main} that
 * calls a user's method with sample arguments -- the same interpreter feature (multiple top-level
 * classes, one holding {@code main}) that already exists for the "no entry point found" message.
 */
public final class JudgeHarnessBuilder {

    /** Every marker line looks like {@code ##ALGOLENS_TC_<index>_OK##<rendered result>} or
     * {@code ##ALGOLENS_TC_<index>_ERR##<exception message>}. */
    public static final String MARKER_PREFIX = "##ALGOLENS_TC_";

    private JudgeHarnessBuilder() {
    }

    public static String build(Problem problem, List<TestCase> testCases, String userCode) {
        List<ParamSpec> parameters = problem.getParameters();
        String receiver = problem.isMethodStatic()
                ? problem.getClassName() + "."
                : "new " + problem.getClassName() + "().";
        String argumentNames = parameters.stream().map(ParamSpec::name)
                .collect(Collectors.joining(", "));
        String call = receiver + problem.getMethodName() + "(" + argumentNames + ")";

        StringBuilder source = new StringBuilder();
        source.append(userCode).append("\n\n");
        source.append("class AlgolensJudgeDriver {\n");
        source.append("    public static void main(String[] args) {\n");

        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            List<String> arguments = testCase.getArguments();
            source.append("        try {\n");
            for (int p = 0; p < parameters.size(); p++) {
                ParamSpec parameter = parameters.get(p);
                String literal = p < arguments.size() ? arguments.get(p) : "null";
                source.append("            ").append(parameter.type()).append(' ')
                        .append(parameter.name()).append(" = ").append(literal).append(";\n");
            }
            source.append("            System.out.println(\"").append(MARKER_PREFIX).append(i)
                    .append("_OK##\" + (").append(call).append("));\n");
            source.append("        } catch (Exception e) {\n");
            source.append("            System.out.println(\"").append(MARKER_PREFIX).append(i)
                    .append("_ERR##\" + e.getMessage());\n");
            source.append("        }\n");
        }

        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }
}
