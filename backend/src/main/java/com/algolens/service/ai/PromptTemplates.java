package com.algolens.service.ai;

import com.algolens.dto.ai.ExplainMode;

/**
 * The prompts. Kept in one file so they can be reviewed and tuned as prompts rather than being
 * scattered through service code as string concatenation.
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /**
     * The instruction that matters most is "the trace is the ground truth". Without it a model
     * asked about bubble sort will happily describe how bubble sort generally works, which reads
     * plausibly and can contradict the specific run on the user's screen -- the single most
     * damaging failure mode for a tool whose whole promise is showing what *your* code did.
     */
    public static final String SYSTEM = """
            You are the explanation panel inside AlgoLens, a step-by-step data-structures and
            algorithms visualizer. A learner has written code, run it, and is looking at one step
            of the execution.

            You are given the source code and an excerpt of the real execution trace their code
            produced. The trace is ground truth. Never contradict it, never describe what the
            algorithm "usually" does when the trace shows otherwise, and never invent variable
            values, indices or comparisons that are not in the excerpt. If the excerpt does not
            contain enough information to answer, say exactly what is missing.

            Style:
            - Address the learner directly, in plain language.
            - Refer to concrete values from the trace ("arr[0] is 5, arr[1] is 2, so they swap"),
              not abstract placeholders.
            - Be brief. Two or three short paragraphs, or a short list. No preamble, no summary
              of what you are about to say.
            - Use markdown sparingly: inline code for identifiers, bold for a key term at most.
            - If the code has a real bug, say so plainly and point at the line.
            """;

    public static String instructionFor(ExplainMode mode, Integer stepIndex) {
        return switch (mode) {
            case EXPLAIN_STEP -> """
                    Explain what happens at step %s and why.

                    Cover: which line ran, what it did to the data, and what that accomplishes for
                    the algorithm as a whole. Then say in one sentence what happens next.
                    """.formatted(stepIndex == null ? "the current step" : stepIndex);

            case EXPLAIN_CODE -> """
                    Walk me through what this algorithm does, using the trace to ground it.

                    Cover: the strategy in one or two sentences, what each loop or recursive call
                    is responsible for, and what the state looks like when it finishes.
                    """;

            case FIND_BUG -> """
                    Review this code for bugs.

                    Use the trace as evidence. If you find a bug, name the line, say what the
                    trace shows going wrong, and give the corrected line. If the code is correct,
                    say so directly and mention any edge case worth testing -- do not invent a
                    problem.
                    """;

            case COMPLEXITY -> """
                    Analyse the time and space complexity.

                    Give the answer in big-O for best, average and worst case, then justify each
                    from the structure of the code. If the trace excerpt reports comparison or
                    swap counts, use them to sanity check your answer and say whether they agree.
                    """;
        };
    }

    /** Assembles the user turn: instruction, code, then trace. */
    public static String userMessage(ExplanationPrompt prompt) {
        StringBuilder message = new StringBuilder();
        message.append(instructionFor(prompt.mode(), prompt.stepIndex())).append('\n');

        message.append("## Code (")
                .append(prompt.language() == null ? "Java" : prompt.language())
                .append(")\n```\n")
                .append(prompt.code())
                .append("\n```\n");

        if (prompt.traceExcerpt() != null && !prompt.traceExcerpt().isEmpty()) {
            message.append("\n## Execution trace excerpt\n");
            for (String line : prompt.traceExcerpt()) {
                message.append(line).append('\n');
            }
        } else {
            message.append("\nNo trace excerpt was provided for this request.\n");
        }

        if (prompt.question() != null && !prompt.question().isBlank()) {
            message.append("\n## The learner also asks\n").append(prompt.question().strip())
                    .append('\n');
        }
        return message.toString();
    }
}
