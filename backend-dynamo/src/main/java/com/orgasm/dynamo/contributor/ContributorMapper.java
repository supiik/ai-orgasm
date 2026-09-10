package com.orgasm.dynamo.contributor;

import com.orgasm.dynamo.domain.IdGenerator;
import org.springframework.stereotype.Component;

@Component
public class ContributorMapper {

    static final String ID_PREFIX = "cont";

    public ContributorResponse toResponse(ContributorItem item) {
        return ContributorResponse.builder()
                .id(IdGenerator.format(ID_PREFIX, item.getId()))
                .name(item.getName())
                .email(item.getEmail())
                .avatarUrl(item.getAvatarUrl())
                .version(item.getVersion())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public ContributorItem toItem(CreateContributorRequest request, long tenantId, long id) {
        ContributorItem item = new ContributorItem();
        item.setPk(ContributorItem.partitionKey(tenantId));
        item.setSk(String.valueOf(id));
        item.setId(id);
        item.setTenantId(tenantId);
        item.setName(request.name());
        item.setEmail(request.email());
        item.setAvatarUrl(request.avatarUrl());
        return item;
    }

    public void updateItem(UpdateContributorRequest request, ContributorItem existing) {
        existing.setName(request.name());
        if (request.email() != null) existing.setEmail(request.email());
        if (request.avatarUrl() != null) existing.setAvatarUrl(request.avatarUrl());
    }
}
