package com.algolens.dto.ai;

/** What the user asked the assistant for. Each maps to a different prompt and credit cost. */
public enum ExplainMode {

    /** "What is happening at this step?" */
    EXPLAIN_STEP,

    /** "Walk me through this whole algorithm." */
    EXPLAIN_CODE,

    /** "Why is my output wrong?" */
    FIND_BUG,

    /** "What is the time and space complexity, and why?" */
    COMPLEXITY
}
