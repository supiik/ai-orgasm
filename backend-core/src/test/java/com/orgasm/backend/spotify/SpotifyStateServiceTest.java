package com.orgasm.backend.spotify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SpotifyStateServiceTest {

    SpotifyStateService service = new SpotifyStateService("00112233445566778899aabbccddeeff");

    @Test
    void mintAndVerify_roundTripsPlaylistId() {
        String state = service.mint("play-abc123");
        assertThat(service.verify(state)).isEqualTo("play-abc123");
    }

    @Test
    void mintAndVerify_roundTripsNullPlaylistId() {
        String state = service.mint(null);
        assertThat(service.verify(state)).isNull();
    }

    @Test
    void verify_rejectsTamperedPayload() {
        String state = service.mint("play-abc123");
        char[] chars = state.toCharArray();
        chars[chars.length / 2] = chars[chars.length / 2] == 'a' ? 'b' : 'a';
        String corrupted = new String(chars);

        assertThatThrownBy(() -> service.verify(corrupted)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verify_rejectsGarbageInput() {
        assertThatThrownBy(() -> service.verify("not-a-valid-state")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verify_rejectsStateSignedWithDifferentSecret() {
        String state = service.mint("play-abc123");
        SpotifyStateService otherService = new SpotifyStateService("ffeeddccbbaa99887766554433221100");

        assertThatThrownBy(() -> otherService.verify(state)).isInstanceOf(IllegalArgumentException.class);
    }
}
