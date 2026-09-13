package com.orgasm.lambda.auth;

/** Missing, malformed, expired, or otherwise invalid bearer token. Maps to HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
