package com.orgasm.billing;

import com.orgasm.backend.BackendCoreTestApplication;
import com.orgasm.billing.domain.Organization;
import com.orgasm.billing.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = BackendCoreTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class OrganizationRepositoryIT {

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
    OrganizationRepository repository;

    @Test
    void findAll_returnsSeededDefaultOrganization() {
        List<Organization> all = repository.findAll();

        assertThat(all).hasSize(1);
        Organization org = all.getFirst();
        assertThat(org.getId()).isEqualTo(1L);
        assertThat(org.getSlug()).isEqualTo("default");
        assertThat(org.getName()).isEqualTo("Default Organization");
    }

    @Test
    void findBySlug_returnsOrganization_whenExists() {
        Optional<Organization> found = repository.findBySlug("default");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(1L);
        assertThat(found.get().getName()).isEqualTo("Default Organization");
    }

    @Test
    void findBySlug_returnsEmpty_whenNotFound() {
        Optional<Organization> found = repository.findBySlug("nonexistent");

        assertThat(found).isEmpty();
    }
}
