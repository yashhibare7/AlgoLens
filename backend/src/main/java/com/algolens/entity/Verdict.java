package com.algolens.entity;

/** Outcome of judging a submission against a problem's test cases. */
public enum Verdict {

    /** Every test case passed. */
    ACCEPTED,

    /** At least one test case produced the wrong output. */
    WRONG_ANSWER,

    /** The submission threw for at least one test case. */
    RUNTIME_ERROR,

    /** The submission did not compile against the interpreter's supported subset. */
    COMPILE_ERROR,

    /** The wall-clock or step budget was exhausted before every test case finished. */
    TIME_LIMIT_EXCEEDED,

    /** Something went wrong on our side, not the submission's. */
    INTERNAL_ERROR;

    public boolean isAccepted() {
        return this == ACCEPTED;
    }
}
