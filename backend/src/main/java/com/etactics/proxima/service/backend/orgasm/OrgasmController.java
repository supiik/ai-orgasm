package com.etactics.proxima.service.backend.orgasm;

import com.etactics.proxima.service.backend.nomination.NominationResponse;
import com.etactics.proxima.service.backend.playlist.PlaylistResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1", version = "1")
@RequiredArgsConstructor
public class OrgasmController {

    private final OrgasmService orgasmService;

    @PostMapping("/playlists/{id}/open")
    public PlaylistResponse openPlaylist(
            @PathVariable String id,
            @RequestBody @Valid OpenPlaylistRequest request) {
        return orgasmService.openPlaylist(id, request);
    }

    @GetMapping("/contributors/{id}/playlists")
    public Page<PlaylistResponse> findPlaylistsByContributor(
            @PathVariable String id, Pageable pageable) {
        return orgasmService.findPlaylistsByContributor(id, pageable);
    }

    @PostMapping("/playlists/{id}/nominations")
    @ResponseStatus(HttpStatus.CREATED)
    public NominationResponse nominateSong(
            @PathVariable String id,
            @RequestBody @Valid NominateSongRequest request) {
        return orgasmService.nominateSong(id, request);
    }

    @GetMapping("/playlists/{id}/nominations")
    public Page<NominationResponse> findNominations(
            @PathVariable String id, Pageable pageable) {
        return orgasmService.findNominations(id, pageable);
    }

    @PutMapping("/nominations/{id}/approve")
    public NominationResponse approveNomination(
            @PathVariable String id,
            @RequestBody @Valid ReviewNominationRequest request) {
        return orgasmService.approveNomination(id, request.reviewerId());
    }

    @PutMapping("/nominations/{id}/decline")
    public NominationResponse declineNomination(
            @PathVariable String id,
            @RequestBody @Valid ReviewNominationRequest request) {
        return orgasmService.declineNomination(id, request.reviewerId());
    }

    @PostMapping("/playlists/{id}/publish")
    public PlaylistResponse publishPlaylist(
            @PathVariable String id,
            @RequestBody @Valid PublishPlaylistRequest request) {
        return orgasmService.publishPlaylist(id, request.contributorId());
    }
}
