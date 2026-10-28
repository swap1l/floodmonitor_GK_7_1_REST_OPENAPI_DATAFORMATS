package com.example.floodmonitor.exception;

/** API-Key mitgeschickt, aber ungültig → 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
