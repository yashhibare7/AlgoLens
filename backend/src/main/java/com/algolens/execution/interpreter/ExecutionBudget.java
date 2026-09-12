package com.algolens.execution.interpreter;

import com.algolens.entity.ExecutionStatus;

/**
 * The interpreter's hard stop.
 *
 * <p>Because user code is interpreted rather than executed, every limit is enforced from inside
 * the evaluation loop: an infinite loop cannot outlast the step budget, and a runaway allocation
 * cannot outlast the array-length cap. The wall-clock check is sampled rather than checked on
 * every step, since {@code System.nanoTime()} is not free and the step budget already bounds the
 * worst case.
 */
public final class ExecutionBudget {

    private static final int CLOCK_CHECK_MASK = 0x3FF; // sample the clock every 1024 steps

    private final long maxSteps;
    private final long deadlineNanos;
    private final int maxCallDepth;
    private final int maxArrayLength;

    private long steps;

    public ExecutionBudget(long maxSteps, long wallClockTimeoutMs, int maxCallDepth,
            int maxArrayLength) {
        this.maxSteps = maxSteps;
        this.deadlineNanos = System.nanoTime() + wallClockTimeoutMs * 1_000_000L;
        this.maxCallDepth = maxCallDepth;
        this.maxArrayLength = maxArrayLength;
    }

    /** Called once per executed statement and once per loop iteration. */
    public void step() {
        steps++;
        if (steps > maxSteps) {
            throw new BudgetExceededException(
                    "Execution stopped after " + maxSteps
                            + " steps. Is there an infinite loop, or is the input too large?",
                    ExecutionStatus.LIMIT_EXCEEDED);
        }
        if ((steps & CLOCK_CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos) {
            throw new BudgetExceededException("Execution timed out", ExecutionStatus.TIMEOUT);
        }
    }

    public void checkCallDepth(int depth) {
        if (depth > maxCallDepth) {
            throw new BudgetExceededException(
                    "Call depth exceeded " + maxCallDepth + " frames (infinite recursion?)",
                    ExecutionStatus.LIMIT_EXCEEDED);
        }
    }

    public void checkArrayLength(long length, int line) {
        if (length < 0) {
            throw new InterpreterException("Negative array size: " + length, line);
        }
        if (length > maxArrayLength) {
            throw new BudgetExceededException(
                    "Array length " + length + " exceeds the limit of " + maxArrayLength,
                    ExecutionStatus.LIMIT_EXCEEDED);
        }
    }

    public long steps() {
        return steps;
    }
}
