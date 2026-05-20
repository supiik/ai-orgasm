package com.etactics.proxima.sal.backend.playlist;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping(value = "/api/v1/playlists", version = "1")
@RequiredArgsConstructor
@Slf4j
public class PlaylistController {

    private final PlaylistService service;

    @GetMapping
    public ResponseEntity<Page<PlaylistResponse>> findAll(
            @ModelAttribute FindPlaylistsRequest request,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.findAll(request, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaylistResponse> findById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PlaylistResponse> create(@RequestBody @Valid CreatePlaylistRequest request) {
        PlaylistResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlaylistResponse> update(
            @PathVariable String id,
            @RequestBody @Valid UpdatePlaylistRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
