package com.orgasm.dynamo.song;

import com.orgasm.dynamo.domain.IdGenerator;
import com.orgasm.dynamo.tenant.DynamoTenantContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
public class SongService {

    private final SongDynamoRepository repository;
    private final SongMapper mapper;

    @CircuitBreaker(name = "db")
    public SongResponse create(CreateSongRequest request) {
        long tenantId = DynamoTenantContext.get();
        long id = IdGenerator.generate();
        Instant now = Instant.now();

        SongItem item = mapper.toItem(request, tenantId, id);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return mapper.toResponse(repository.save(item));
    }

    @CircuitBreaker(name = "db")
    public SongResponse create(UnaryOperator<CreateSongRequest.CreateSongRequestBuilder> customizer) {
        return create(customizer.apply(CreateSongRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    public Optional<SongResponse> findById(String id) {
        long tenantId = DynamoTenantContext.get();
        return repository.findById(tenantId, IdGenerator.parse(id))
                .filter(item -> item.getDeletedAt() == null)
                .map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    public Page<SongResponse> findAll(FindSongsRequest request, Pageable pageable) {
        long tenantId = DynamoTenantContext.get();
        List<SongItem> items = repository.findAllByTenant(tenantId).stream()
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> request.name() == null || request.name().isBlank()
                        || item.getName() != null
                                && item.getName().toLowerCase().contains(request.name().toLowerCase()))
                .toList();

        if (pageable.isUnpaged()) {
            return new PageImpl<>(items.stream().map(mapper::toResponse).toList());
        }

        int from = Math.min((int) pageable.getOffset(), items.size());
        int to = Math.min(from + pageable.getPageSize(), items.size());
        List<SongResponse> page = items.subList(from, to).stream().map(mapper::toResponse).toList();

        return new PageImpl<>(page, pageable, items.size());
    }

    @CircuitBreaker(name = "db")
    public Page<SongResponse> findAll(
            UnaryOperator<FindSongsRequest.FindSongsRequestBuilder> customizer, Pageable pageable) {
        return findAll(customizer.apply(FindSongsRequest.builder()).build(), pageable);
    }

    @CircuitBreaker(name = "db")
    public SongResponse update(String id, UpdateSongRequest request) {
        SongItem existing = requireById(id);
        mapper.updateItem(request, existing);
        existing.setUpdatedAt(Instant.now());
        return mapper.toResponse(repository.save(existing));
    }

    @CircuitBreaker(name = "db")
    public SongResponse update(String id, UnaryOperator<UpdateSongRequest.UpdateSongRequestBuilder> customizer) {
        return update(id, customizer.apply(UpdateSongRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    public void delete(String id) {
        SongItem existing = requireById(id);
        existing.setDeletedAt(Instant.now());
        repository.save(existing);
    }

    private SongItem requireById(String id) {
        long tenantId = DynamoTenantContext.get();
        return repository.findById(tenantId, IdGenerator.parse(id))
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Song not found: " + id));
    }
}
