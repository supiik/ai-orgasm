package com.orgasm.dynamo.song;

import com.orgasm.dynamo.domain.IdGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SongMapperTest {

    private final SongMapper mapper = new SongMapper();

    @Test
    void toItem_populatesKeysAndFields() {
        var request = new CreateSongRequest("Some Artist", "My Song", "My Album", 2020, "http://example.com/song");

        var item = mapper.toItem(request, 7L, 42L);

        assertThat(item.getPk()).isEqualTo("7#SONG");
        assertThat(item.getSk()).isEqualTo("42");
        assertThat(item.getId()).isEqualTo(42L);
        assertThat(item.getTenantId()).isEqualTo(7L);
        assertThat(item.getArtist()).isEqualTo("Some Artist");
        assertThat(item.getName()).isEqualTo("My Song");
        assertThat(item.getAlbum()).isEqualTo("My Album");
        assertThat(item.getReleaseYear()).isEqualTo(2020);
        assertThat(item.getUrl()).isEqualTo("http://example.com/song");
    }

    @Test
    void toItem_keepsOptionalFieldsNull() {
        var request = new CreateSongRequest("Some Artist", "My Song", null, null, null);

        var item = mapper.toItem(request, 1L, 1L);

        assertThat(item.getAlbum()).isNull();
        assertThat(item.getReleaseYear()).isNull();
        assertThat(item.getUrl()).isNull();
    }

    @Test
    void toResponse_mapsFieldsAndFormatsId() {
        var item = new SongItem();
        item.setId(42L);
        item.setArtist("Some Artist");
        item.setName("My Song");
        item.setAlbum("My Album");
        item.setReleaseYear(2020);
        item.setUrl("http://example.com/song");
        item.setVersion(3L);
        item.setCreatedAt(Instant.EPOCH);
        item.setUpdatedAt(Instant.EPOCH);

        var response = mapper.toResponse(item);

        assertThat(response.id()).isEqualTo(IdGenerator.format("song", 42L));
        assertThat(response.artist()).isEqualTo("Some Artist");
        assertThat(response.name()).isEqualTo("My Song");
        assertThat(response.album()).isEqualTo("My Album");
        assertThat(response.releaseYear()).isEqualTo(2020);
        assertThat(response.url()).isEqualTo("http://example.com/song");
        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(response.updatedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void toResponse_handlesNullOptionalFields() {
        var item = new SongItem();
        item.setId(1L);

        var response = mapper.toResponse(item);

        assertThat(response.album()).isNull();
        assertThat(response.releaseYear()).isNull();
        assertThat(response.url()).isNull();
    }

    @Test
    void updateItem_overwritesRequiredFields_andIgnoresNullOptionalFields() {
        var existing = new SongItem();
        existing.setArtist("Old Artist");
        existing.setName("Old Name");
        existing.setAlbum("Old Album");
        existing.setReleaseYear(1999);
        existing.setUrl("http://old.example.com");

        mapper.updateItem(new UpdateSongRequest("New Artist", "New Name", null, null, null), existing);

        assertThat(existing.getArtist()).isEqualTo("New Artist");
        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getAlbum()).isEqualTo("Old Album");
        assertThat(existing.getReleaseYear()).isEqualTo(1999);
        assertThat(existing.getUrl()).isEqualTo("http://old.example.com");
    }

    @Test
    void updateItem_overwritesOptionalFields_whenProvided() {
        var existing = new SongItem();
        existing.setArtist("Old Artist");
        existing.setName("Old Name");
        existing.setAlbum("Old Album");
        existing.setReleaseYear(1999);
        existing.setUrl("http://old.example.com");

        mapper.updateItem(new UpdateSongRequest("New Artist", "New Name", "New Album", 2024, "http://new.example.com"), existing);

        assertThat(existing.getAlbum()).isEqualTo("New Album");
        assertThat(existing.getReleaseYear()).isEqualTo(2024);
        assertThat(existing.getUrl()).isEqualTo("http://new.example.com");
    }
}
