package com.example.floodmonitor.exception;

/** Kein API-Key mitgeschickt → 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
