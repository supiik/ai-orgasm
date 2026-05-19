package com.orgasm.backend.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.accept.MissingApiVersionException;
import org.springframework.web.accept.NotAcceptableApiVersionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<Map<String, String>> handleCircuitOpen(CallNotPermittedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Service temporarily unavailable"));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(MissingApiVersionException.class)
    public ResponseEntity<Map<String, String>> handleMissingVersion(MissingApiVersionException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "API version is required"));
    }

    @ExceptionHandler(InvalidApiVersionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidVersion(InvalidApiVersionException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Unsupported API version: " + ex.getMessage()));
    }

    @ExceptionHandler(NotAcceptableApiVersionException.class)
    public ResponseEntity<Map<String, String>> handleNotAcceptableVersion(NotAcceptableApiVersionException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(Map.of("error", "API version not acceptable: " + ex.getMessage()));
    }
}
