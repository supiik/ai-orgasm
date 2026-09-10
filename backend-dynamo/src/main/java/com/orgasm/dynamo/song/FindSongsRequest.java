package com.orgasm.dynamo.song;

import lombok.Builder;

@Builder
public record FindSongsRequest(String name) {}
