package com.orgasm.backend.contributor;

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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional("appTransactionManager")
class ContributorRepositoryIT {

    @Container
    static MariaDBContainer<?> appDb = new MariaDBContainer<>("mariadb:12.2.2")
            .withDatabaseName("orgasm")
            .withUsername("orgasm")
            .withPassword("orgasm");

    @Container
    static MariaDBContainer<?> billingDb = new MariaDBContainer<>("mariadb:12.2.2")
            .withDatabaseName("orgasm_billing")
            .withUsername("orgasm")
            .withPassword("orgasm");

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
    ContributorRepository repository;

    @Test
    void save_persistsContributor() {
        Contributor saved = repository.save(new Contributor(null, "Alice", "alice@example.com", null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findById_returnsContributor_afterSave() {
        Contributor saved = repository.save(new Contributor(null, "Bob", null, null));

        assertThat(repository.findById(saved.getId())).contains(saved);
    }

    @Test
    void findAll_returnsPaginatedResults() {
        repository.save(new Contributor(null, "A", null, null));
        repository.save(new Contributor(null, "B", null, null));
        repository.save(new Contributor(null, "C", null, null));

        Page<Contributor> page = repository.findAll(PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void softDelete_hidesRowFromSubsequentFinds() {
        Contributor saved = repository.save(new Contributor(null, "To Delete", null, null));
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

    @Test
    void findByNameContainingIgnoreCase_returnsMatches() {
        repository.save(new Contributor(null, "Alice Smith", null, null));
        repository.save(new Contributor(null, "Bob Jones", null, null));
        repository.save(new Contributor(null, "Alicia Keys", null, null));

        Page<Contributor> result = repository.findByNameContainingIgnoreCase("alic", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Contributor::getName)
                .containsExactlyInAnyOrder("Alice Smith", "Alicia Keys");
    }

    @Test
    void findByNameContainingIgnoreCase_isCaseInsensitive() {
        repository.save(new Contributor(null, "Thom Yorke", null, null));

        Page<Contributor> result = repository.findByNameContainingIgnoreCase("THOM", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(result.getContent()).extracting(Contributor::getName).contains("Thom Yorke");
    }
}
