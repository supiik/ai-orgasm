package com.orgasm.dynamo.contributor;

import lombok.Builder;

@Builder
public record FindContributorsRequest(String name) {}
