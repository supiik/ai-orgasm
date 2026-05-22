package com.pi2.anchor.backend.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional("appTransactionManager")
class SampleRepositoryIT {

    @Container
    static MariaDBContainer<?> appDb = new MariaDBContainer<>("mariadb:12.2.2")
            .withDatabaseName("anchor")
            .withUsername("anchor")
            .withPassword("anchor");

    @Container
    static MariaDBContainer<?> billingDb = new MariaDBContainer<>("mariadb:12.2.2")
            .withDatabaseName("anchor_billing")
            .withUsername("anchor")
            .withPassword("anchor");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.app.url", appDb::getJdbcUrl);
        registry.add("datasource.app.username", appDb::getUsername);
        registry.add("datasource.app.password", appDb::getPassword);
        registry.add("datasource.app.driver-class-name", () -> "org.mariadb.jdbc.Driver");

        registry.add("datasource.billing.url", billingDb::getJdbcUrl);
        registry.add("datasource.billing.username", billingDb::getUsername);
        registry.add("datasource.billing.password", billingDb::getPassword);
        registry.add("datasource.billing.driver-class-name", () -> "org.mariadb.jdbc.Driver");

        registry.add("datasource.flyway.enabled", () -> "true");
    }

    @Autowired
    SampleRepository repository;

    static Sample newSample(String name, SampleStatus status) {
        return new Sample(null, null, name, "desc", "test@example.com", 1, 100L, 5.0,
                new BigDecimal("9.99"), true, LocalDate.of(2000, 1, 1),
                LocalDateTime.of(2024, 6, 1, 10, 0), status, "notes");
    }

    @Test
    void save_persistsSample() {
        Sample saved = repository.save(newSample("Widget", SampleStatus.DRAFT));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Widget");
    }

    @Test
    void findById_returnsSample_afterSave() {
        Sample saved = repository.save(newSample("Find Me", SampleStatus.DRAFT));

        assertThat(repository.findById(saved.getId())).contains(saved);
    }

    @Test
    void findAll_returnsPaginatedResults() {
        repository.save(newSample("A", SampleStatus.DRAFT));
        repository.save(newSample("B", SampleStatus.ACTIVE));
        repository.save(newSample("C", SampleStatus.ARCHIVED));

        Page<Sample> page = repository.findAll(PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void findByStatus_returnsMatchingRecords() {
        repository.save(newSample("Draft One", SampleStatus.DRAFT));
        repository.save(newSample("Active One", SampleStatus.ACTIVE));
        repository.save(newSample("Draft Two", SampleStatus.DRAFT));

        Page<Sample> result = repository.findByStatus(SampleStatus.DRAFT, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Sample::getName)
                .contains("Draft One", "Draft Two")
                .doesNotContain("Active One");
    }

    @Test
    void findByNameContainingIgnoreCase_returnsMatches() {
        repository.save(newSample("Blue Widget", SampleStatus.DRAFT));
        repository.save(newSample("Red Gadget", SampleStatus.ACTIVE));
        repository.save(newSample("Blue Gadget", SampleStatus.DRAFT));

        Page<Sample> result = repository.findByNameContainingIgnoreCase("blue", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Sample::getName)
                .containsExactlyInAnyOrder("Blue Widget", "Blue Gadget");
    }

    @Test
    void softDelete_hidesRowFromSubsequentFinds() {
        Sample saved = repository.save(newSample("To Delete", SampleStatus.DRAFT));
        repository.flush();

        int affected = repository.softDeleteById(saved.getId(), Instant.now());
        repository.flush();

        assertThat(affected).isEqualTo(1);
        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    @Test
    void softDelete_returnsZero_whenIdNotFound() {
        int affected = repository.softDeleteById(0L, Instant.now());
        assertThat(affected).isEqualTo(0);
    }
}
