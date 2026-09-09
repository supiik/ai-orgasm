package com.orgasm.dynamo.playlist;

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
import java.util.Optional;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistDynamoRepository repository;
    private final PlaylistMapper mapper;

    @CircuitBreaker(name = "db")
    public PlaylistResponse create(CreatePlaylistRequest request) {
        long tenantId = DynamoTenantContext.get();
        long id = IdGenerator.generate();
        Instant now = Instant.now();

        PlaylistItem item = mapper.toItem(request, tenantId, id);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return mapper.toResponse(repository.save(item));
    }

    @CircuitBreaker(name = "db")
    public PlaylistResponse create(UnaryOperator<CreatePlaylistRequest.CreatePlaylistRequestBuilder> customizer) {
        return create(customizer.apply(CreatePlaylistRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    public Optional<PlaylistResponse> findById(String id) {
        long tenantId = DynamoTenantContext.get();
        return repository.findById(tenantId, IdGenerator.parse(id))
                .filter(item -> item.getDeletedAt() == null)
                .map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    public Page<PlaylistResponse> findAll(FindPlaylistsRequest request, Pageable pageable) {
        long tenantId = DynamoTenantContext.get();
        List<PlaylistItem> items = repository.findAllByTenant(tenantId).stream()
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
        List<PlaylistResponse> page = items.subList(from, to).stream().map(mapper::toResponse).toList();

        return new PageImpl<>(page, pageable, items.size());
    }

    @CircuitBreaker(name = "db")
    public Page<PlaylistResponse> findAll(
            UnaryOperator<FindPlaylistsRequest.FindPlaylistsRequestBuilder> customizer, Pageable pageable) {
        return findAll(customizer.apply(FindPlaylistsRequest.builder()).build(), pageable);
    }
}
