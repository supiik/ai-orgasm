package com.etactics.proxima.service.backend.song;

import com.etactics.proxima.service.backend.domain.IdGenerator;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class SongMapperTest {

    private final SongMapper mapper = Mappers.getMapper(SongMapper.class);

    @Test
    void toResponse_returnsNull_whenInputNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_copiesAllFields() {
        Song song = new Song(3L, null, "Radiohead", "Creep", "Pablo Honey", 1993);

        SongResponse response = mapper.toResponse(song);

        assertThat(response).isNotNull();
        assertThat(response.id()).startsWith("song-");
        assertThat(IdGenerator.parse(response.id())).isEqualTo(3L);
        assertThat(response.artist()).isEqualTo("Radiohead");
        assertThat(response.name()).isEqualTo("Creep");
        assertThat(response.album()).isEqualTo("Pablo Honey");
        assertThat(response.releaseYear()).isEqualTo(1993);
    }

    @Test
    void toEntity_returnsNull_whenInputNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toEntity_copiesAllFields() {
        CreateSongRequest request = new CreateSongRequest("Nirvana", "Smells Like Teen Spirit", "Nevermind", 1991);

        Song entity = mapper.toEntity(request);

        assertThat(entity).isNotNull();
        assertThat(entity.getArtist()).isEqualTo("Nirvana");
        assertThat(entity.getName()).isEqualTo("Smells Like Teen Spirit");
        assertThat(entity.getAlbum()).isEqualTo("Nevermind");
        assertThat(entity.getReleaseYear()).isEqualTo(1991);
    }

    @Test
    void toEntity_handlesNullOptionalFields() {
        CreateSongRequest request = new CreateSongRequest("Artist", "Track", null, null);

        Song entity = mapper.toEntity(request);

        assertThat(entity.getAlbum()).isNull();
        assertThat(entity.getReleaseYear()).isNull();
    }

    @Test
    void updateEntity_isNoOp_whenRequestNull() {
        Song song = new Song(1L, null, "Original Artist", "Original Name", "Original Album", 2000);

        mapper.updateEntity(null, song);

        assertThat(song.getArtist()).isEqualTo("Original Artist");
        assertThat(song.getName()).isEqualTo("Original Name");
        assertThat(song.getAlbum()).isEqualTo("Original Album");
        assertThat(song.getReleaseYear()).isEqualTo(2000);
    }

    @Test
    void updateEntity_updatesAllFields() {
        Song song = new Song(1L, null, "Old Artist", "Old Name", "Old Album", 1990);
        UpdateSongRequest request = new UpdateSongRequest("New Artist", "New Name", "New Album", 2020);

        mapper.updateEntity(request, song);

        assertThat(song.getId()).isEqualTo(1L);
        assertThat(song.getArtist()).isEqualTo("New Artist");
        assertThat(song.getName()).isEqualTo("New Name");
        assertThat(song.getAlbum()).isEqualTo("New Album");
        assertThat(song.getReleaseYear()).isEqualTo(2020);
    }

    @Test
    void updateEntity_preservesOptionalFields_whenNullInRequest() {
        Song song = new Song(1L, null, "Artist", "Name", "Album", 2000);
        UpdateSongRequest request = new UpdateSongRequest("New Artist", "New Name", null, null);

        mapper.updateEntity(request, song);

        assertThat(song.getAlbum()).isEqualTo("Album");
        assertThat(song.getReleaseYear()).isEqualTo(2000);
    }
}
