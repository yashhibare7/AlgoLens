package com.algolens.service.ai;

/** The configured provider could not answer. Callers fall back rather than failing the request. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }
}
