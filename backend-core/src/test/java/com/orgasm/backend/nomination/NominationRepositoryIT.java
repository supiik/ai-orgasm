package com.orgasm.backend.nomination;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.contributor.ContributorRepository;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.playlist.PlaylistRepository;
import com.orgasm.backend.playlist.PlaylistStatus;
import com.orgasm.backend.song.Song;
import com.orgasm.backend.song.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional("appTransactionManager")
class NominationRepositoryIT {

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

    @Autowired NominationRepository nominationRepository;
    @Autowired PlaylistRepository playlistRepository;
    @Autowired SongRepository songRepository;
    @Autowired ContributorRepository contributorRepository;

    Playlist playlist;
    Song song;
    Contributor contributor;

    @BeforeEach
    void setUp() {
        playlist = playlistRepository.save(new Playlist(null, "Test Playlist", null, PlaylistStatus.OPEN, null, null));
        song = songRepository.save(new Song(null, "Artist", "Track", null, null));
        contributor = contributorRepository.save(new Contributor(null, "Alice", null, null));
    }

    @Test
    void save_persistsNomination() {
        Nomination saved = nominationRepository.save(
                new Nomination(null, playlist.getId(), song.getId(), contributor.getId(), null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(NominationStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByPlaylistId_returnsNominations() {
        nominationRepository.save(new Nomination(null, playlist.getId(), song.getId(), contributor.getId(), null));

        var page = nominationRepository.findByPlaylistId(playlist.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSongId()).isEqualTo(song.getId());
    }

    @Test
    void existsByPlaylistIdAndSongId_returnsTrue_whenNominated() {
        nominationRepository.save(new Nomination(null, playlist.getId(), song.getId(), contributor.getId(), null));
        nominationRepository.flush();

        assertThat(nominationRepository.existsByPlaylistIdAndSongId(playlist.getId(), song.getId())).isTrue();
    }

    @Test
    void existsByPlaylistIdAndSongId_returnsFalse_whenNotNominated() {
        assertThat(nominationRepository.existsByPlaylistIdAndSongId(playlist.getId(), song.getId())).isFalse();
    }
}
