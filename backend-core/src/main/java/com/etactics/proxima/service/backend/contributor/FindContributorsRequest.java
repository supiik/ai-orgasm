package com.etactics.proxima.service.backend.contributor;

import lombok.Builder;

@Builder
public record FindContributorsRequest(String name) {}
