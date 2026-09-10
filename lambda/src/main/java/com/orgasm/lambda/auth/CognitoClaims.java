package com.orgasm.lambda.auth;

/** Claims extracted from a validated Cognito ID token. */
public record CognitoClaims(String sub, String email) {
}
