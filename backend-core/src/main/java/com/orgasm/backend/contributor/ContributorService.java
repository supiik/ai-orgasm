package com.orgasm.backend.contributor;

import com.orgasm.backend.domain.ForbiddenException;
import com.orgasm.backend.domain.IdGenerator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.function.UnaryOperator;

@Service
@Transactional("appTransactionManager")
@RequiredArgsConstructor
@Slf4j
public class ContributorService {

    private final ContributorRepository repository;
    private final ContributorMapper mapper;

    @CircuitBreaker(name = "db")
    public ContributorResponse create(CreateContributorRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @CircuitBreaker(name = "db")
    public ContributorResponse create(UnaryOperator<CreateContributorRequest.CreateContributorRequestBuilder> customizer) {
        return create(customizer.apply(CreateContributorRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Optional<ContributorResponse> findById(String id) {
        return repository.findById(IdGenerator.parse(id)).map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Optional<ContributorResponse> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<ContributorResponse> findAll(FindContributorsRequest request, Pageable pageable) {
        if (request.name() == null || request.name().isBlank()) {
            return repository.findAll(pageable).map(mapper::toResponse);
        }
        return repository.findByNameContainingIgnoreCase(request.name(), pageable).map(mapper::toResponse);
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public Page<ContributorResponse> findAll(
            UnaryOperator<FindContributorsRequest.FindContributorsRequestBuilder> customizer, Pageable pageable) {
        return findAll(customizer.apply(FindContributorsRequest.builder()).build(), pageable);
    }

    @CircuitBreaker(name = "db")
    public ContributorResponse update(String id, UpdateContributorRequest request) {
        Contributor existing = requireById(id);
        mapper.updateEntity(request, existing);
        return mapper.toResponse(repository.save(existing));
    }

    @CircuitBreaker(name = "db")
    public ContributorResponse update(String id, UnaryOperator<UpdateContributorRequest.UpdateContributorRequestBuilder> customizer) {
        return update(id, customizer.apply(UpdateContributorRequest.builder()).build());
    }

    @CircuitBreaker(name = "db")
    @Transactional(readOnly = true)
    public boolean isOrgAdmin(String email) {
        return repository.findByEmail(email).map(Contributor::isOrgAdmin).orElse(false);
    }

    /**
     * Self-service promote/demote: the acting contributor (resolved by their own email, same as
     * {@link #findByEmail}) must already be an org admin themselves. The target is resolved by id
     * through the same Hibernate tenant filter as every other lookup, so a cross-tenant id simply
     * 404s rather than needing an explicit same-org check here.
     */
    @CircuitBreaker(name = "db")
    public ContributorResponse setOrgAdmin(String actingEmail, String targetId, boolean isOrgAdmin) {
        Contributor acting = repository.findByEmail(actingEmail)
                .orElseThrow(() -> new ForbiddenException("No contributor linked to this account"));
        if (!acting.isOrgAdmin()) {
            throw new ForbiddenException("Only an organization admin can do this");
        }
        Contributor target = requireById(targetId);
        target.setOrgAdmin(isOrgAdmin);
        return mapper.toResponse(repository.save(target));
    }

    @CircuitBreaker(name = "db")
    public void delete(String id) {
        if (repository.softDeleteById(IdGenerator.parse(id), Instant.now()) == 0) {
            throw new EntityNotFoundException("Contributor not found: " + id);
        }
    }

    private Contributor requireById(String id) {
        return repository.findById(IdGenerator.parse(id))
                .orElseThrow(() -> new EntityNotFoundException("Contributor not found: " + id));
    }
}
