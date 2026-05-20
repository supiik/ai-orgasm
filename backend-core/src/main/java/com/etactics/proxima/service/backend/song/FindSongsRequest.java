package com.etactics.proxima.service.backend.song;

import lombok.Builder;

@Builder
public record FindSongsRequest(String name) {}
