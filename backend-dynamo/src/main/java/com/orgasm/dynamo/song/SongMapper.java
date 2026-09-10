package com.orgasm.dynamo.song;

import com.orgasm.dynamo.domain.IdGenerator;
import org.springframework.stereotype.Component;

@Component
public class SongMapper {

    static final String ID_PREFIX = "song";

    public SongResponse toResponse(SongItem item) {
        return SongResponse.builder()
                .id(IdGenerator.format(ID_PREFIX, item.getId()))
                .artist(item.getArtist())
                .name(item.getName())
                .album(item.getAlbum())
                .releaseYear(item.getReleaseYear())
                .url(item.getUrl())
                .version(item.getVersion())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public SongItem toItem(CreateSongRequest request, long tenantId, long id) {
        SongItem item = new SongItem();
        item.setPk(SongItem.partitionKey(tenantId));
        item.setSk(String.valueOf(id));
        item.setId(id);
        item.setTenantId(tenantId);
        item.setArtist(request.artist());
        item.setName(request.name());
        item.setAlbum(request.album());
        item.setReleaseYear(request.releaseYear());
        item.setUrl(request.url());
        return item;
    }

    public void updateItem(UpdateSongRequest request, SongItem existing) {
        existing.setArtist(request.artist());
        existing.setName(request.name());
        if (request.album() != null) existing.setAlbum(request.album());
        if (request.releaseYear() != null) existing.setReleaseYear(request.releaseYear());
        if (request.url() != null) existing.setUrl(request.url());
    }
}
