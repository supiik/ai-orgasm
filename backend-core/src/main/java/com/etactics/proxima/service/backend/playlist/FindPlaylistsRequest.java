package com.etactics.proxima.service.backend.playlist;

import lombok.Builder;

@Builder
public record FindPlaylistsRequest(String name) {}
