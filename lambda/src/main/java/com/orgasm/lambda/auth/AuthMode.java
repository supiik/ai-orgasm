package com.orgasm.lambda.auth;

/** Declares what {@link com.orgasm.lambda.BaseHandler#handleRequest} requires before {@code execute()} runs. */
public enum AuthMode {

    /** No token check at all. */
    PUBLIC,

    /** A valid Cognito ID token is required, but it need not resolve to a linked Contributor
     * (e.g. the link endpoint itself, called right after Cognito sign-up). */
    AUTHENTICATED,

    /** A valid Cognito ID token is required AND must resolve to an existing linked
     * Contributor; {@link com.orgasm.dynamo.tenant.DynamoTenantContext} is set from that
     * Contributor's tenant before {@code execute()} runs. The default for business endpoints. */
    AUTHENTICATED_WITH_CONTRIBUTOR
}
