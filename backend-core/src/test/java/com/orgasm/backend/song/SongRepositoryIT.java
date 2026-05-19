package com.orgasm.backend.song;

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
class SongRepositoryIT {

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
    SongRepository repository;

    @Test
    void save_persistsSong() {
        Song saved = repository.save(new Song(null, "Radiohead", "Creep", "Pablo Honey", 1993));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findById_returnsSong_afterSave() {
        Song saved = repository.save(new Song(null, "Nirvana", "Smells Like Teen Spirit", null, null));

        assertThat(repository.findById(saved.getId())).contains(saved);
    }

    @Test
    void findAll_returnsPaginatedResults() {
        repository.save(new Song(null, "Artist A", "Track 1", null, null));
        repository.save(new Song(null, "Artist B", "Track 2", null, null));
        repository.save(new Song(null, "Artist C", "Track 3", null, null));

        Page<Song> page = repository.findAll(PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void softDelete_hidesRowFromSubsequentFinds() {
        Song saved = repository.save(new Song(null, "Oasis", "Wonderwall", "What's the Story Morning Glory?", 1995));
        repository.flush();

        int affected = repository.softDeleteById(saved.getId(), Instant.now());
        repository.flush();

        assertThat(affected).isEqualTo(1);
        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    @Test
    void softDelete_returnsZero_whenIdNotFound() {
        int affected = repository.softDeleteById("song-nonexistent", Instant.now());

        assertThat(affected).isEqualTo(0);
    }

    @Test
    void findByNameContainingIgnoreCase_returnsMatches() {
        repository.save(new Song(null, "Radiohead", "Creep", null, null));
        repository.save(new Song(null, "Radiohead", "Karma Police", null, null));
        repository.save(new Song(null, "Nirvana", "Come as You Are", null, null));

        Page<Song> result = repository.findByNameContainingIgnoreCase("cree", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Song::getName).containsExactly("Creep");
    }

    @Test
    void findByNameContainingIgnoreCase_isCaseInsensitive() {
        repository.save(new Song(null, "Radiohead", "Paranoid Android", null, null));

        Page<Song> result = repository.findByNameContainingIgnoreCase("PARANOID", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(result.getContent()).extracting(Song::getName).contains("Paranoid Android");
    }
}
