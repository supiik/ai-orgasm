package com.orgasm.backend.playlist;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.domain.IdGenerator;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PlaylistMapperTest {

    private final PlaylistMapper mapper = Mappers.getMapper(PlaylistMapper.class);

    static Contributor contributor(long id) {
        var c = new Contributor();
        c.setId(id);
        return c;
    }

    @Test
    void toResponse_returnsNull_whenInputNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_copiesAllFields() {
        Playlist playlist = new Playlist(7L, null, "Workout", "Pump up", PlaylistStatus.OPEN, contributor(2L), Instant.EPOCH, null, null);

        PlaylistResponse response = mapper.toResponse(playlist);

        assertThat(response).isNotNull();
        assertThat(response.id()).startsWith("play-");
        assertThat(IdGenerator.parse(response.id())).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Workout");
        assertThat(response.description()).isEqualTo("Pump up");
        assertThat(response.status()).isEqualTo(PlaylistStatus.OPEN);
        assertThat(response.leadContributorId()).startsWith("cont-");
        assertThat(IdGenerator.parse(response.leadContributorId())).isEqualTo(2L);
        assertThat(response.deadline()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void toResponse_handlesNullLeadContributor() {
        Playlist playlist = new Playlist(1L, null, "Mix", null, PlaylistStatus.NEW, null, null, null, null);

        PlaylistResponse response = mapper.toResponse(playlist);

        assertThat(response.leadContributorId()).isNull();
        assertThat(response.deadline()).isNull();
    }

    @Test
    void toEntity_returnsNull_whenInputNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toEntity_defaultsStatusToNew_whenNotProvided() {
        CreatePlaylistRequest request = new CreatePlaylistRequest("Chill", "Lo-fi", null, null);

        Playlist entity = mapper.toEntity(request);

        assertThat(entity).isNotNull();
        assertThat(entity.getName()).isEqualTo("Chill");
        assertThat(entity.getDescription()).isEqualTo("Lo-fi");
        assertThat(entity.getStatus()).isEqualTo(PlaylistStatus.NEW);
    }

    @Test
    void toEntity_usesProvidedStatus() {
        CreatePlaylistRequest request = new CreatePlaylistRequest("Chill", "Lo-fi", PlaylistStatus.OPEN, null);

        Playlist entity = mapper.toEntity(request);

        assertThat(entity.getStatus()).isEqualTo(PlaylistStatus.OPEN);
    }

    @Test
    void updateEntity_isNoOp_whenRequestNull() {
        Playlist playlist = new Playlist(1L, null, "Original", "Original desc", PlaylistStatus.NEW, null, null, null, null);

        mapper.updateEntity(null, playlist);

        assertThat(playlist.getName()).isEqualTo("Original");
        assertThat(playlist.getDescription()).isEqualTo("Original desc");
        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.NEW);
    }

    @Test
    void updateEntity_updatesAllFields() {
        Playlist playlist = new Playlist(1L, null, "Original", "Original desc", PlaylistStatus.NEW, null, null, null, null);
        UpdatePlaylistRequest request = new UpdatePlaylistRequest("Renamed", "New desc", PlaylistStatus.CLOSED);

        mapper.updateEntity(request, playlist);

        assertThat(playlist.getId()).isEqualTo(1L);
        assertThat(playlist.getName()).isEqualTo("Renamed");
        assertThat(playlist.getDescription()).isEqualTo("New desc");
        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.CLOSED);
    }

    @Test
    void updateEntity_preservesStatus_whenNullInRequest() {
        Playlist playlist = new Playlist(1L, null, "Original", "desc", PlaylistStatus.OPEN, null, null, null, null);
        UpdatePlaylistRequest request = new UpdatePlaylistRequest("Renamed", "desc", null);

        mapper.updateEntity(request, playlist);

        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.OPEN);
    }
}
