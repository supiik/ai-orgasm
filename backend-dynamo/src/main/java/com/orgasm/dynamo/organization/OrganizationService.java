package com.orgasm.dynamo.organization;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationDynamoRepository repository;

    @CircuitBreaker(name = "db")
    public List<OrganizationResponse> findAll() {
        return repository.findAll().stream()
                .map(item -> OrganizationResponse.builder()
                        .id(item.getId())
                        .slug(item.getSlug())
                        .name(item.getName())
                        .build())
                .toList();
    }

    @CircuitBreaker(name = "db")
    public Optional<OrganizationItem> findBySlug(String slug) {
        return repository.findBySlug(slug);
    }
}
