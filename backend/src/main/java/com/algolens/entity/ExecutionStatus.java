package com.algolens.entity;

/** Outcome of one code submission. Mirrored verbatim in the frontend trace contract. */
public enum ExecutionStatus {

    /** Program ran to completion inside every limit. */
    SUCCESS,

    /** Lexer/parser rejected the source before anything ran. */
    COMPILE_ERROR,

    /** Program started but blew up (index out of bounds, divide by zero, ...). */
    RUNTIME_ERROR,

    /** Wall clock budget exhausted. */
    TIMEOUT,

    /** Step, event, output or recursion-depth budget exhausted. */
    LIMIT_EXCEEDED,

    /** The requested language has no executor registered yet. */
    UNSUPPORTED_LANGUAGE,

    /** Something went wrong on our side. */
    INTERNAL_ERROR;

    public boolean isFailure() {
        return this != SUCCESS;
    }
}
