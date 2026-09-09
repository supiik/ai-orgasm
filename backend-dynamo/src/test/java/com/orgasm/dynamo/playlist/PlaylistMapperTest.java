package com.orgasm.dynamo.playlist;

import com.orgasm.dynamo.domain.IdGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PlaylistMapperTest {

    private final PlaylistMapper mapper = new PlaylistMapper();

    @Test
    void toItem_populatesKeysAndDefaultsStatusToNew() {
        var request = new CreatePlaylistRequest("My Mix", "desc", null, RatingType.LINEAR);

        var item = mapper.toItem(request, 7L, 42L);

        assertThat(item.getPk()).isEqualTo("7#PLAYLIST");
        assertThat(item.getSk()).isEqualTo("42");
        assertThat(item.getId()).isEqualTo(42L);
        assertThat(item.getTenantId()).isEqualTo(7L);
        assertThat(item.getName()).isEqualTo("My Mix");
        assertThat(item.getDescription()).isEqualTo("desc");
        assertThat(item.getStatus()).isEqualTo("NEW");
        assertThat(item.getRatingType()).isEqualTo("LINEAR");
    }

    @Test
    void toItem_keepsExplicitStatus() {
        var request = new CreatePlaylistRequest("My Mix", null, PlaylistStatus.OPEN, null);

        var item = mapper.toItem(request, 1L, 1L);

        assertThat(item.getStatus()).isEqualTo("OPEN");
        assertThat(item.getRatingType()).isNull();
    }

    @Test
    void toResponse_mapsFieldsAndFormatsId() {
        var item = new PlaylistItem();
        item.setId(42L);
        item.setName("My Mix");
        item.setDescription("desc");
        item.setStatus("OPEN");
        item.setRatingType("FIBONACCI");
        item.setVersion(3L);
        item.setCreatedAt(Instant.EPOCH);
        item.setUpdatedAt(Instant.EPOCH);

        var response = mapper.toResponse(item);

        assertThat(response.id()).isEqualTo(IdGenerator.format("play", 42L));
        assertThat(response.name()).isEqualTo("My Mix");
        assertThat(response.description()).isEqualTo("desc");
        assertThat(response.status()).isEqualTo(PlaylistStatus.OPEN);
        assertThat(response.ratingType()).isEqualTo(RatingType.FIBONACCI);
        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(response.updatedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void toResponse_handlesNullStatusAndRatingType() {
        var item = new PlaylistItem();
        item.setId(1L);

        var response = mapper.toResponse(item);

        assertThat(response.status()).isNull();
        assertThat(response.ratingType()).isNull();
    }
}
