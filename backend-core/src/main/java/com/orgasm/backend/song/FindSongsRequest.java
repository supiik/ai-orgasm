package com.orgasm.backend.song;

import lombok.Builder;

@Builder
public record FindSongsRequest(String name) {}
