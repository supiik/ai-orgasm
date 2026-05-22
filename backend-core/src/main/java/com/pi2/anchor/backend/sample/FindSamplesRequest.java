package com.pi2.anchor.backend.sample;

import lombok.Builder;

@Builder
public record FindSamplesRequest(
        String name,
        SampleStatus status
) {}
