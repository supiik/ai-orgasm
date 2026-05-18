package com.orgasm.backend.playlist;

import lombok.Builder;

@Builder
public record FindPlaylistsRequest(String name) {}
