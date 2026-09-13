package com.orgasm.dynamo.organization;

import lombok.Builder;

@Builder
public record OrganizationResponse(
        Long id,
        String slug,
        String name
) {}
