package com.orgasm.dynamo.playlist;

import lombok.Builder;

@Builder
public record FindPlaylistsRequest(String name) {}
