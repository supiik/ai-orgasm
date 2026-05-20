package com.etactics.proxima.sal.backend.song;

import lombok.Builder;

@Builder
public record FindSongsRequest(String name) {}
