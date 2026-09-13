package com.orgasm.dynamo.playlist;

import com.orgasm.dynamo.domain.IdGenerator;
import org.springframework.stereotype.Component;

@Component
public class PlaylistMapper {

    static final String ID_PREFIX = "play";

    public PlaylistResponse toResponse(PlaylistItem item) {
        return PlaylistResponse.builder()
                .id(IdGenerator.format(ID_PREFIX, item.getId()))
                .name(item.getName())
                .description(item.getDescription())
                .status(item.getStatus() != null ? PlaylistStatus.valueOf(item.getStatus()) : null)
                .ratingType(item.getRatingType() != null ? RatingType.valueOf(item.getRatingType()) : null)
                .leadContributorId(item.getLeadContributorId() != null
                        ? IdGenerator.format("cont", item.getLeadContributorId())
                        : null)
                .deadline(item.getDeadline())
                .guessingDeadline(item.getGuessingDeadline())
                .version(item.getVersion())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public PlaylistItem toItem(CreatePlaylistRequest request, long tenantId, long id) {
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

    public void updateItem(UpdatePlaylistRequest request, PlaylistItem existing) {
        existing.setName(request.name());
        if (request.description() != null) existing.setDescription(request.description());
        if (request.status() != null) existing.setStatus(request.status().name());
    }
}
