package com.orgasm.lambda.auth;

/**
 * The bearer token is valid, but no Contributor is linked to it yet. Distinct from
 * {@link UnauthorizedException}: re-authenticating won't fix this, the client needs to call
 * the link endpoint first. Maps to HTTP 403.
 */
public class ContributorNotLinkedException extends RuntimeException {

    public ContributorNotLinkedException(String message) {
        super(message);
    }
}
