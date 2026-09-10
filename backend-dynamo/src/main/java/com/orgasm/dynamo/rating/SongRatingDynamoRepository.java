package com.orgasm.dynamo.rating;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SongRatingDynamoRepository {

    private final DynamoDbTable<SongRatingItem> table;
    private final DynamoDbIndex<SongRatingItem> songRatingsByPlaylistIndex;

    /**
     * Uses updateItem (not putItem) because the Enhanced Client only returns the
     * post-write item state — with the version attribute populated — from updateItem;
     * putItem returns void and never mutates the Java object passed to it.
     */
    public SongRatingItem save(SongRatingItem item) {
        return table.updateItem(item);
    }

    public List<SongRatingItem> findByPlaylistId(long playlistId) {
        return songRatingsByPlaylistIndex.query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(playlistId)
                        .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    public void deleteByPlaylistAndContributor(long playlistId, long contributorId) {
        findByPlaylistId(playlistId).stream()
                .filter(item -> item.getContributorId() != null && item.getContributorId() == contributorId)
                .forEach(item -> table.deleteItem(Key.builder()
                        .partitionValue(item.getPk())
                        .sortValue(item.getSk())
                        .build()));
    }
}
