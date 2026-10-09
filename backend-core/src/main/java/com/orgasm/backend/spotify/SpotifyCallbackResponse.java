package com.orgasm.backend.spotify;

import lombok.Builder;

/** {@code playlistId} is the value the caller passed to {@code GET /spotify/connect?playlistId=}
 * (carried through Spotify's redirect inside the signed {@code state} param) so the UI knows
 * which playlist page to return to; {@code null} if the connect flow wasn't started from one. */
@Builder
public record SpotifyCallbackResponse(String playlistId) {}
