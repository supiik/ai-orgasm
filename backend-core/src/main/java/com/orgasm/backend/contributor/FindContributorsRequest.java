package com.orgasm.backend.contributor;

import lombok.Builder;

@Builder
public record FindContributorsRequest(String name) {}
