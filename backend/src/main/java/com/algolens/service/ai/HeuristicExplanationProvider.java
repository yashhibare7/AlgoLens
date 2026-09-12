package com.algolens.service.ai;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * A deterministic, offline explainer used when no Anthropic API key is configured, or when the
 * API call fails.
 *
 * <p>It exists so a fresh clone is a working product rather than a broken button. It reads the
 * trace excerpt and the shape of the code and says only what it can actually establish -- loop
 * nesting for complexity, the recorded message for a step. It never speculates, and every answer
 * it returns is labelled as a local analysis so nobody mistakes it for a model's reasoning.
 */
@Component
public class HeuristicExplanationProvider implements ExplanationProvider {

    private static final Pattern LOOP = Pattern.compile("\\b(for|while)\\s*\\(");

    /**
     * "A name declared with a body that calls itself." The leading negative lookahead is load
     * bearing: without it, {@code for (...) { ... for (...) }} matches -- the outer {@code for}
     * being followed by an inner {@code for} looks exactly like self-recursion to this pattern --
     * and every nested loop gets reported as recursive.
     */
    private static final Pattern RECURSIVE_HINT = Pattern.compile(
            "(?s)\\b(?!(?:if|for|while|switch|catch|return|else|new|synchronized)\\b)"
                    + "(\\w+)\\s*\\([^)]*\\)\\s*\\{.*?\\b\\1\\s*\\(");

    @Override
    public String name() {
        return "heuristic";
    }

    @Override
    public String model() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String explain(ExplanationPrompt prompt) {
        String body = switch (prompt.mode()) {
            case EXPLAIN_STEP -> explainStep(prompt);
            case EXPLAIN_CODE -> explainCode(prompt);
            case COMPLEXITY -> explainComplexity(prompt);
            case FIND_BUG -> explainBug(prompt);
        };
        return body + "\n\n---\n*Local analysis. Set `ANTHROPIC_API_KEY` on the backend for "
                + "full AI explanations.*";
    }

    private String explainStep(ExplanationPrompt prompt) {
        List<String> excerpt = prompt.traceExcerpt();
        if (excerpt == null || excerpt.isEmpty()) {
            return "There is no trace to read for this step. Run the code first, then ask again.";
        }
        StringBuilder out = new StringBuilder();
        out.append("**This step**\n\n");
        // The current step is the last line the frontend sends in its window.
        out.append("- ").append(excerpt.get(excerpt.size() - 1)).append('\n');
        if (excerpt.size() > 1) {
            out.append("\n**How it got here**\n\n");
            excerpt.subList(Math.max(0, excerpt.size() - 4), excerpt.size() - 1)
                    .forEach(line -> out.append("- ").append(line).append('\n'));
        }
        return out.toString();
    }

    private String explainCode(ExplanationPrompt prompt) {
        int loops = count(LOOP, prompt.code());
        boolean recursive = RECURSIVE_HINT.matcher(prompt.code()).find();
        StringBuilder out = new StringBuilder("**Structure of this code**\n\n");
        out.append("- ").append(loops).append(loops == 1 ? " loop" : " loops")
                .append(" in the source\n");
        out.append("- ").append(recursive ? "appears to recurse" : "no recursion detected")
                .append('\n');
        if (prompt.traceExcerpt() != null && !prompt.traceExcerpt().isEmpty()) {
            out.append("- ").append(prompt.traceExcerpt().size())
                    .append(" trace lines supplied for context\n");
        }
        out.append("\nStep through the trace with the playback controls to see the data change; ")
                .append("the variables panel shows exactly which value moved at each step.");
        return out.toString();
    }

    private String explainComplexity(ExplanationPrompt prompt) {
        int depth = maxLoopNesting(prompt.code());
        boolean recursive = RECURSIVE_HINT.matcher(prompt.code()).find();
        String estimate = switch (depth) {
            case 0 -> recursive ? "recursive -- depends on how the input shrinks per call" : "O(1)";
            case 1 -> "O(n)";
            case 2 -> "O(n^2)";
            case 3 -> "O(n^3)";
            default -> "O(n^" + depth + ")";
        };
        return """
                **Estimated time complexity: %s**

                Derived from the code's structure: maximum loop nesting depth is %d%s.

                This is a structural estimate, not a proof -- a loop whose bound halves each
                iteration is O(log n) even though it nests once. Check the comparison and swap
                counters next to the timeline: for a quadratic sort they should grow roughly with
                the square of the input size when you re-run with a longer array.
                """.formatted(estimate, depth,
                recursive ? ", and the code appears to recurse" : "");
    }

    private String explainBug(ExplanationPrompt prompt) {
        StringBuilder out = new StringBuilder();
        out.append("A local analysis cannot review logic reliably, so here is what it can ")
                .append("check for you:\n\n");
        String code = prompt.code();
        boolean flagged = false;
        if (code.contains("<=") && LOOP.matcher(code).find()) {
            out.append("- There is a `<=` in a loop condition. If it bounds an array index, ")
                    .append("check it is not `i <= arr.length` (off by one).\n");
            flagged = true;
        }
        if (code.contains("length - 1") || code.contains("length-1")) {
            out.append("- `length - 1` appears. Confirm the last element really should be ")
                    .append("excluded at that point.\n");
            flagged = true;
        }
        if (!flagged) {
            out.append("- No common off-by-one pattern spotted in the source.\n");
        }
        out.append("\nThe trace itself is the better evidence: step to where the data first ")
                .append("looks wrong and read the message on that step.");
        return out.toString();
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int total = 0;
        while (matcher.find()) {
            total++;
        }
        return total;
    }

    /**
     * Brace-counting approximation of loop nesting. Good enough for the estimate it is used for,
     * and it says so; it is confused by braces inside string literals, which is acceptable for a
     * fallback that never claims to be authoritative.
     */
    private static int maxLoopNesting(String code) {
        int depth = 0;
        int maxDepth = 0;
        int braces = 0;
        int[] loopBraceDepth = new int[64];
        int loopCount = 0;

        Matcher matcher = LOOP.matcher(code);
        int cursor = 0;
        while (cursor < code.length()) {
            char c = code.charAt(cursor);
            if (c == '{') {
                braces++;
            } else if (c == '}') {
                braces--;
                while (loopCount > 0 && loopBraceDepth[loopCount - 1] > braces) {
                    loopCount--;
                    depth--;
                }
            } else if ((c == 'f' || c == 'w') && matcher.find(cursor) && matcher.start() == cursor
                    && loopCount < loopBraceDepth.length) {
                loopBraceDepth[loopCount++] = braces;
                depth++;
                maxDepth = Math.max(maxDepth, depth);
            }
            cursor++;
        }
        return maxDepth;
    }
}
