package com.orgasm.lambda.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitoJwtValidatorTest {

    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test";
    private static final String CLIENT_ID = "test-client-id";

    private RSAKey signingKey;
    private CognitoJwtValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        var jwkSource = new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey.toPublicJWK()));
        validator = new CognitoJwtValidator(ISSUER, CLIENT_ID, jwkSource);
    }

    private String token(JWTClaimsSet.Builder claims) throws Exception {
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build();
        var jwt = new SignedJWT(header, claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private JWTClaimsSet.Builder validClaims() {
        Date now = new Date();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject("sub-123")
                .claim("token_use", "id")
                .claim("email", "alice@example.com")
                .issueTime(now)
                .expirationTime(new Date(now.getTime() + 3_600_000));
    }

    @Test
    void validatesAndExtractsSubAndEmail() throws Exception {
        var claims = validator.validate(token(validClaims()));

        assertThat(claims.sub()).isEqualTo("sub-123");
        assertThat(claims.email()).isEqualTo("alice@example.com");
    }

    @Test
    void rejectsMissingToken() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> validator.validate("  ")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> validator.validate("not-a-jwt")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        var claims = validClaims().issuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_other");

        assertThatThrownBy(() -> validator.validate(token(claims))).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        var claims = validClaims().audience("some-other-client");

        assertThatThrownBy(() -> validator.validate(token(claims))).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAccessTokenUse() throws Exception {
        var claims = validClaims().claim("token_use", "access");

        assertThatThrownBy(() -> validator.validate(token(claims))).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        Date past = new Date(System.currentTimeMillis() - 3_600_000);
        var claims = validClaims().expirationTime(past);

        assertThatThrownBy(() -> validator.validate(token(claims))).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsMissingSubject() throws Exception {
        Date now = new Date();
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .claim("token_use", "id")
                .issueTime(now)
                .expirationTime(new Date(now.getTime() + 3_600_000));

        assertThatThrownBy(() -> validator.validate(token(claims))).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsTokenSignedByAnUntrustedKey() throws Exception {
        RSAKey untrustedKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(untrustedKey.getKeyID()).build();
        var jwt = new SignedJWT(header, validClaims().build());
        jwt.sign(new RSASSASigner(untrustedKey));

        assertThatThrownBy(() -> validator.validate(jwt.serialize())).isInstanceOf(UnauthorizedException.class);
    }
}
