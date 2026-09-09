package com.orgasm.dynamo.playlist;

import com.orgasm.dynamo.domain.IdGenerator;
import org.springframework.stereotype.Component;

@Component
class PlaylistMapper {

    private static final String ID_PREFIX = "play";

    PlaylistResponse toResponse(PlaylistItem item) {
        return PlaylistResponse.builder()
                .id(IdGenerator.format(ID_PREFIX, item.getId()))
                .name(item.getName())
                .description(item.getDescription())
                .status(item.getStatus() != null ? PlaylistStatus.valueOf(item.getStatus()) : null)
                .ratingType(item.getRatingType() != null ? RatingType.valueOf(item.getRatingType()) : null)
                .version(item.getVersion())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    PlaylistItem toItem(CreatePlaylistRequest request, long tenantId, long id) {
        PlaylistItem item = new PlaylistItem();
        item.setPk(PlaylistItem.partitionKey(tenantId));
        item.setSk(String.valueOf(id));
        item.setId(id);
        item.setTenantId(tenantId);
        item.setName(request.name());
        item.setDescription(request.description());
        item.setStatus((request.status() != null ? request.status() : PlaylistStatus.NEW).name());
        item.setRatingType(request.ratingType() != null ? request.ratingType().name() : null);
        return item;
    }
}
