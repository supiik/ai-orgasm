package com.orgasm.lambda.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Set;

/**
 * Validates a Cognito ID token: RS256 signature against the User Pool's JWKS, issuer, audience
 * (the User Pool Client id — carried as {@code aud} on ID tokens; access tokens carry
 * {@code client_id} instead, not {@code aud}), expiry, and {@code token_use=id} (the
 * discriminator between Cognito's ID and access tokens — both are structurally similar RS256
 * JWTs signed by the same JWKS, only {@code token_use} tells them apart).
 * <p>
 * Deliberately validates the ID token rather than the access token: only the ID token reliably
 * carries the {@code email} claim the link flow needs, and using one token type for every
 * authenticated call keeps this a single code path. This is a deviation from strict OAuth
 * semantics (ID tokens identify the user to the client; access tokens authorize API calls) —
 * acceptable here since there is no scope/resource-server model. A future switch to access
 * tokens would need to validate {@code client_id} instead of {@code aud}.
 */
@Component
public class CognitoJwtValidator {

    private final DefaultJWTProcessor<SecurityContext> processor;

    @Autowired
    public CognitoJwtValidator(
            @Value("${app.cognito.user-pool-id}") String userPoolId,
            @Value("${app.cognito.region}") String region,
            @Value("${app.cognito.client-id}") String clientId,
            @Value("${app.cognito.jwks-url-override:}") String jwksUrlOverride) {
        String issuer = "https://cognito-idp.%s.amazonaws.com/%s".formatted(region, userPoolId);
        String jwksUrl = jwksUrlOverride.isBlank() ? issuer + "/.well-known/jwks.json" : jwksUrlOverride;
        this(issuer, clientId, resolveJwkSource(jwksUrl));
    }

    /** Test seam: build the processor from an in-memory {@link JWKSource} instead of a real HTTPS JWKS fetch. */
    CognitoJwtValidator(String issuer, String clientId, JWKSource<SecurityContext> jwkSource) {
        var claimsVerifier = new DefaultJWTClaimsVerifier<SecurityContext>(
                clientId,
                new JWTClaimsSet.Builder().issuer(issuer).claim("token_use", "id").build(),
                Set.of("sub", "token_use", "exp"));
        claimsVerifier.setMaxClockSkew(60);

        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        processor.setJWTClaimsSetVerifier(claimsVerifier);
        this.processor = processor;
    }

    private static JWKSource<SecurityContext> resolveJwkSource(String jwksUrl) {
        try {
            return JWKSourceBuilder.<SecurityContext>create(URI.create(jwksUrl).toURL()).build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Cognito JWKS URL: " + jwksUrl, e);
        }
    }

    public CognitoClaims validate(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new UnauthorizedException("Missing bearer token");
        }
        try {
            JWTClaimsSet claims = processor.process(idToken, null);
            String sub = claims.getSubject();
            if (sub == null) {
                throw new UnauthorizedException("Token missing sub claim");
            }
            return new CognitoClaims(sub, claims.getStringClaim("email"));
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new UnauthorizedException("Invalid token: " + e.getMessage(), e);
        }
    }
}
