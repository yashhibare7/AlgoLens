package com.algolens.trace;

/** Thrown when a run produces more trace events or output than the configured budget allows. */
public class TraceLimitExceededException extends RuntimeException {

    public TraceLimitExceededException(String message) {
        super(message);
    }
}
