package com.orgasm.backend.spotify;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mints/verifies the OAuth {@code state} query parameter as an HMAC-SHA256-signed, base64url
 * payload — {@code nonce:issuedAt:playlistId:signature}. The acting contributor is deliberately
 * NOT encoded here: both {@code /spotify/connect} and {@code /spotify/callback} are authenticated
 * endpoints that resolve the contributor from the caller's own JWT (same as every other endpoint
 * in this app), so {@code state} only needs to (a) survive the Spotify round trip carrying which
 * playlist to redirect back to and (b) prove it was minted by this server and hasn't expired —
 * a CSRF check, not an identity carrier.
 */
@Component
public class SpotifyStateService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final byte[] secretKey;

    public SpotifyStateService(@Value("${app.spotify.state-secret:0000000000000000}") String secretHex) {
        this.secretKey = HexFormat.of().parseHex(secretHex);
    }

    public String mint(String playlistId) {
        long nonce = ThreadLocalRandom.current().nextLong();
        long issuedAt = Instant.now().getEpochSecond();
        String payload = nonce + ":" + issuedAt + ":" + (playlistId == null ? "" : playlistId);
        String raw = payload + ":" + sign(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @return the {@code playlistId} carried in the state, or {@code null} if none was set. */
    public String verify(String state) {
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Malformed state parameter");
        }
        String[] parts = raw.split(":", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Malformed state parameter");
        }
        String payload = parts[0] + ":" + parts[1] + ":" + parts[2];
        String signature = parts[3];
        if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8), sign(payload).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid state signature");
        }
        long issuedAt = Long.parseLong(parts[1]);
        if (Instant.now().getEpochSecond() - issuedAt > TTL.toSeconds()) {
            throw new IllegalArgumentException("State has expired");
        }
        return parts[2].isEmpty() ? null : parts[2];
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign Spotify OAuth state", ex);
        }
    }
}
