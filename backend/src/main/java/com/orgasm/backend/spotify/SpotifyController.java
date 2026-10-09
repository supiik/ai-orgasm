package com.orgasm.backend.spotify;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.contributor.ContributorRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Every endpoint here resolves the acting contributor from the caller's own JWT — never from a
 * request param/body, unlike the pre-existing (and known-broken) {@code contributorId} fields on
 * {@code OrgasmController}'s request bodies. Uses {@code @AuthenticationPrincipal Jwt} (the
 * actual principal type for a resource-server JWT authentication), not
 * {@code JwtAuthenticationToken} as {@code ContributorController.me()} does — that type resolves
 * {@code Authentication.getPrincipal()}, which for a JWT auth IS the {@link Jwt}, not the token
 * wrapper; with Spring Security's default {@code errorOnInvalidType=false} the mismatch silently
 * resolves to {@code null} instead of throwing, so {@code ContributorController.me()}'s version
 * of this pattern only doesn't NPE because nothing currently exercises it under a real JWT. */
@RestController
@RequestMapping(value = "/api/v1/spotify", version = "1")
@RequiredArgsConstructor
public class SpotifyController {

    private final SpotifyService spotifyService;
    private final ContributorRepository contributorRepository;

    @GetMapping("/connect")
    public SpotifyAuthorizeResponse connect(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String playlistId) {
        requireContributor(jwt);
        return spotifyService.getAuthorizeUrl(playlistId);
    }

    @GetMapping("/status")
    public SpotifyConnectionStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        return spotifyService.getConnectionStatus(requireContributor(jwt));
    }

    @PostMapping("/callback")
    public SpotifyCallbackResponse callback(
            @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid SpotifyCallbackRequest request) {
        return spotifyService.handleCallback(requireContributor(jwt), request);
    }

    @PostMapping("/playlists/{id}/export")
    public SpotifyExportResponse export(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return spotifyService.exportPlaylist(id, requireContributor(jwt));
    }

    private Contributor requireContributor(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new EntityNotFoundException("No email claim on the authenticated user");
        }
        return contributorRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Contributor not found for the authenticated user"));
    }
}
