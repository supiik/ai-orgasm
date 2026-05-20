package com.etactics.proxima.sal.backend.playlist;

import lombok.Builder;

@Builder
public record FindPlaylistsRequest(String name) {}
