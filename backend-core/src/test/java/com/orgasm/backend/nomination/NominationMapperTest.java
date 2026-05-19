package com.orgasm.backend.nomination;

import com.orgasm.backend.domain.IdGenerator;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class NominationMapperTest {

    private final NominationMapper mapper = Mappers.getMapper(NominationMapper.class);

    @Test
    void toResponse_returnsNull_whenInputNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_formatsAllPrefixedIds() {
        Nomination nomination = new Nomination(10L, 1L, 2L, 3L, NominationStatus.PENDING);

        NominationResponse response = mapper.toResponse(nomination);

        assertThat(response).isNotNull();
        assertThat(response.id()).startsWith("nom-");
        assertThat(IdGenerator.parse(response.id())).isEqualTo(10L);
        assertThat(response.playlistId()).startsWith("play-");
        assertThat(IdGenerator.parse(response.playlistId())).isEqualTo(1L);
        assertThat(response.songId()).startsWith("song-");
        assertThat(IdGenerator.parse(response.songId())).isEqualTo(2L);
        assertThat(response.nominatedById()).startsWith("cont-");
        assertThat(IdGenerator.parse(response.nominatedById())).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(NominationStatus.PENDING);
    }

    @Test
    void toResponse_mapsApprovedStatus() {
        Nomination nomination = new Nomination(1L, 2L, 3L, 4L, NominationStatus.APPROVED);

        assertThat(mapper.toResponse(nomination).status()).isEqualTo(NominationStatus.APPROVED);
    }

    @Test
    void toResponse_mapsDeclinedStatus() {
        Nomination nomination = new Nomination(1L, 2L, 3L, 4L, NominationStatus.DECLINED);

        assertThat(mapper.toResponse(nomination).status()).isEqualTo(NominationStatus.DECLINED);
    }
}
