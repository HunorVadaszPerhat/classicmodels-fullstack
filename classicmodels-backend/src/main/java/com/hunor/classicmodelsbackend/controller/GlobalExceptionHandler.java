package com.hunor.classicmodelsbackend.controller;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Application-wide exception → HTTP-response translator.
 *
 * <p>{@code @RestControllerAdvice} is Spring's "intercept exceptions
 * thrown from any controller in this app, and translate them to HTTP
 * responses" mechanism. It saves us from littering try/catch in every
 * controller method.</p>
 *
 * <p>For now this only handles optimistic-lock failures. As the app
 * grows, more {@code @ExceptionHandler} methods get added here for
 * other recoverable error types (validation failures,
 * not-found, etc.).</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Optimistic-lock failures map to HTTP 409 Conflict — the standard
     * code for "your request couldn't be processed because the resource
     * is in a different state than you expected."
     *
     * <p>Body shape mirrors RFC 9457 (Problem Details for HTTP APIs)
     * loosely — enough fields for the frontend to display a useful
     * message without needing a strict spec.</p>
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "type", "optimistic-lock-failure",
                "title", "Stale data",
                "status", 409,
                "detail", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * "I looked, the thing isn't there." Maps to HTTP 404. Used by
     * the CLV endpoint when a customer has no order history (and so
     * has no lifetime-value snapshot to return), and by anywhere else
     * a service throws this from a missing-row lookup.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "type", "not-found",
                "title", "Not found",
                "status", 404,
                "detail", ex.getMessage() != null ? ex.getMessage() : "Resource not found",
                "timestamp", Instant.now().toString()
        ));
    }
}
