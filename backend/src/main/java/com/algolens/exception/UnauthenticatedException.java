package com.algolens.exception;

/** Thrown when an endpoint that tolerates anonymous callers decides this one needs an account. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
