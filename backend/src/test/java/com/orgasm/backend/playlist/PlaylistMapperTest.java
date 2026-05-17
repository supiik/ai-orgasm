package com.orgasm.backend.playlist;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class PlaylistMapperTest {

    private final PlaylistMapper mapper = Mappers.getMapper(PlaylistMapper.class);

    @Test
    void toResponse_returnsNull_whenInputNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_copiesAllFields() {
        Playlist playlist = new Playlist(7L, "Workout", "Pump up");

        PlaylistResponse response = mapper.toResponse(playlist);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Workout");
        assertThat(response.description()).isEqualTo("Pump up");
    }

    @Test
    void toEntity_returnsNull_whenInputNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toEntity_copiesNameAndDescription() {
        CreatePlaylistRequest request = new CreatePlaylistRequest("Chill", "Lo-fi");

        Playlist entity = mapper.toEntity(request);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getName()).isEqualTo("Chill");
        assertThat(entity.getDescription()).isEqualTo("Lo-fi");
    }

    @Test
    void updateEntity_isNoOp_whenRequestNull() {
        Playlist playlist = new Playlist(1L, "Original", "Original desc");

        mapper.updateEntity(null, playlist);

        assertThat(playlist.getName()).isEqualTo("Original");
        assertThat(playlist.getDescription()).isEqualTo("Original desc");
    }

    @Test
    void updateEntity_updatesNameAndDescription() {
        Playlist playlist = new Playlist(1L, "Original", "Original desc");
        UpdatePlaylistRequest request = new UpdatePlaylistRequest("Renamed", "New desc");

        mapper.updateEntity(request, playlist);

        assertThat(playlist.getId()).isEqualTo(1L);
        assertThat(playlist.getName()).isEqualTo("Renamed");
        assertThat(playlist.getDescription()).isEqualTo("New desc");
    }
}
