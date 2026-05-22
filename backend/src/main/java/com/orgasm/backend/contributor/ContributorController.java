package com.orgasm.backend.contributor;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping(value = "/api/v1/contributors", version = "1")
@RequiredArgsConstructor
@Slf4j
public class ContributorController {

    private final ContributorService service;

    @GetMapping("/me")
    public ResponseEntity<ContributorResponse> me(
            @AuthenticationPrincipal JwtAuthenticationToken authentication) {
        String email = authentication.getTokenAttributes()
                .getOrDefault("email", "").toString();
        if (email.isBlank()) return ResponseEntity.unprocessableEntity().build();
        return service.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<ContributorResponse>> findAll(
            @ModelAttribute FindContributorsRequest request,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(service.findAll(request, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContributorResponse> findById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ContributorResponse> create(@RequestBody @Valid CreateContributorRequest request) {
        ContributorResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContributorResponse> update(
            @PathVariable String id,
            @RequestBody @Valid UpdateContributorRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
