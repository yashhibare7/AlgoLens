package com.algolens.exception;

/**
 * Thrown when a user asks for something that costs more credits than they hold. Carries the
 * numbers so the API can tell the client exactly what is missing rather than a bare 402.
 */
public class InsufficientCreditsException extends RuntimeException {

    private final int required;
    private final int available;

    public InsufficientCreditsException(int required, int available) {
        super("This action costs " + required + " credit(s) but only " + available
                + " remain. Credits reset at the start of each month.");
        this.required = required;
        this.available = available;
    }

    public int required() {
        return required;
    }

    public int available() {
        return available;
    }
}
