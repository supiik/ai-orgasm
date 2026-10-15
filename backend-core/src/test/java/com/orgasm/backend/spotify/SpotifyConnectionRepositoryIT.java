package com.orgasm.backend.spotify;

import static org.assertj.core.api.Assertions.assertThat;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.contributor.ContributorRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional("appTransactionManager")
class SpotifyConnectionRepositoryIT {

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

    @Autowired SpotifyConnectionRepository repository;
    @Autowired ContributorRepository contributorRepository;

    Contributor savedContributor() {
        return contributorRepository.save(new Contributor(null, null, "Thom Yorke", "thom@example.com", null, false));
    }

    @Test
    void save_persistsConnection_withVersionAndTimestamps() {
        Contributor contributor = savedContributor();
        SpotifyConnection connection = new SpotifyConnection();
        connection.setContributor(contributor);
        connection.setSpotifyUserId("spotify-user-1");
        connection.setAccessToken("access-token");
        connection.setRefreshToken("refresh-token");
        connection.setExpiresAt(Instant.now().plusSeconds(3600));

        SpotifyConnection saved = repository.save(connection);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByContributor_Id_returnsConnection_afterSave() {
        Contributor contributor = savedContributor();
        SpotifyConnection connection = new SpotifyConnection();
        connection.setContributor(contributor);
        connection.setSpotifyUserId("spotify-user-1");
        connection.setAccessToken("access-token");
        connection.setRefreshToken("refresh-token");
        connection.setExpiresAt(Instant.now().plusSeconds(3600));
        repository.save(connection);

        var found = repository.findByContributor_Id(contributor.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getSpotifyUserId()).isEqualTo("spotify-user-1");
    }

    @Test
    void findByContributor_Id_returnsEmpty_whenNoConnection() {
        Contributor contributor = savedContributor();
        assertThat(repository.findByContributor_Id(contributor.getId())).isEmpty();
    }
}
