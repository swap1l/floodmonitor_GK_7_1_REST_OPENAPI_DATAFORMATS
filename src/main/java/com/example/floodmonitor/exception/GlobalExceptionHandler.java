package com.example.floodmonitor.exception;

import com.example.floodmonitor.config.ApiKeyInterceptor;
import com.example.floodmonitor.model.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Arrays;

/**
 * Zentrale Fehlerbehandlung: alle Fehler werden als {@link ApiError} (JSON oder XML) ohne Stacktrace geliefert.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({StationNotFoundException.class, NoMeasurementException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalid(InvalidRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(StationConflictException.class)
    public ResponseEntity<ApiError> handleConflict(StationConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "ApiKey header=\"" + ApiKeyInterceptor.HEADER + "\"")
                .body(new ApiError(401, HttpStatus.UNAUTHORIZED.getReasonPhrase(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /** Request-Body ist kein gültiges JSON/XML oder passt nicht zum erwarteten Aufbau. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Request-Body fehlt oder ist kein gültiges JSON/XML", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Ungültiger Wert '" + ex.getValue() + "' für Parameter '" + ex.getName() + "'";
        Class<?> type = ex.getRequiredType();
        if (type != null && type.isEnum()) {
            message += ", erlaubt: " + Arrays.toString(type.getEnumConstants());
        } else if (type == Instant.class) {
            message += ", erwartet ISO-8601, z. B. 2026-05-01T08:00:00Z";
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** Der Client akzeptiert weder JSON noch XML – daher ohne Body antworten. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable() {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            // Standard-Fehler von Spring MVC (404 für unbekannte Pfade, 405, 415, ...)
            HttpStatusCode status = errorResponse.getStatusCode();
            String detail = errorResponse.getBody().getDetail();
            return build(HttpStatus.valueOf(status.value()), detail != null ? detail : ex.getMessage(), request);
        }
        log.error("Unerwarteter Fehler bei {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Interner Serverfehler", request);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
