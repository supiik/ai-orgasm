package com.orgasm.backend.nomination;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.domain.IdGenerator;
import com.orgasm.backend.playlist.Playlist;
import com.orgasm.backend.playlist.PlaylistStatus;
import com.orgasm.backend.song.Song;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class NominationMapperTest {

    private final NominationMapper mapper = Mappers.getMapper(NominationMapper.class);

    static Playlist playlist(long id) {
        var p = new Playlist();
        p.setId(id);
        p.setStatus(PlaylistStatus.OPEN);
        return p;
    }

    static Song song(long id) {
        var s = new Song();
        s.setId(id);
        return s;
    }

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
    void toResponse_formatsAllPrefixedIds() {
        var nomination = new Nomination(10L, null, playlist(1L), song(2L), contributor(3L), NominationStatus.PENDING);

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
        var nomination = new Nomination(1L, null, playlist(2L), song(3L), contributor(4L), NominationStatus.APPROVED);

        assertThat(mapper.toResponse(nomination).status()).isEqualTo(NominationStatus.APPROVED);
    }

    @Test
    void toResponse_mapsDeclinedStatus() {
        var nomination = new Nomination(1L, null, playlist(2L), song(3L), contributor(4L), NominationStatus.DECLINED);

        assertThat(mapper.toResponse(nomination).status()).isEqualTo(NominationStatus.DECLINED);
    }
}
