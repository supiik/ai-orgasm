package com.orgasm.backend.playlist;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional("appTransactionManager")
@RequiredArgsConstructor
@Slf4j
public class PlaylistService {

    private final PlaylistRepository repository;

    public Playlist create(Playlist playlist) {
        return repository.save(playlist);
    }

    @Transactional(readOnly = true)
    public Optional<Playlist> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Playlist> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Playlist update(Long id, Playlist updates) {
        Playlist existing = requireById(id);
        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        return repository.save(existing);
    }

    public void delete(Long id) {
        if (repository.softDeleteById(id, Instant.now()) == 0) {
            throw new EntityNotFoundException("Playlist not found: " + id);
        }
    }

    private Playlist requireById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Playlist not found: " + id));
    }
}
