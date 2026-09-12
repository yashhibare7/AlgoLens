package com.algolens.execution.interpreter;

import com.algolens.entity.ExecutionStatus;

/**
 * Raised when a submission runs past one of its hard limits. Carries the status the API should
 * report, so a wall-clock overrun surfaces as TIMEOUT while a step-count overrun surfaces as
 * LIMIT_EXCEEDED.
 */
public class BudgetExceededException extends RuntimeException {

    private final ExecutionStatus status;

    public BudgetExceededException(String message, ExecutionStatus status) {
        super(message);
        this.status = status;
    }

    public ExecutionStatus status() {
        return status;
    }
}
