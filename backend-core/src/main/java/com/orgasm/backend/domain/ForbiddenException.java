package com.orgasm.backend.domain;

/** Mapped to 403 by {@code GlobalExceptionHandler} — the caller is authenticated but not allowed to perform this action. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
