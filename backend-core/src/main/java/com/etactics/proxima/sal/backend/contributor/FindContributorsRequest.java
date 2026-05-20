package com.etactics.proxima.sal.backend.contributor;

import lombok.Builder;

@Builder
public record FindContributorsRequest(String name) {}
