package com.orgasm.dynamo.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PlaylistRankingDynamoRepository {

    private final DynamoDbTable<PlaylistRankingItem> table;
    private final DynamoDbIndex<PlaylistRankingItem> playlistRankingsByPlaylistIndex;

    /**
     * Uses updateItem (not putItem) because the Enhanced Client only returns the
     * post-write item state — with the version attribute populated — from updateItem;
     * putItem returns void and never mutates the Java object passed to it.
     */
    public PlaylistRankingItem save(PlaylistRankingItem item) {
        return table.updateItem(item);
    }

    /** Sort key on the byPlaylist GSI is rankPosition, so the Query already returns ascending order. */
    public List<PlaylistRankingItem> findByPlaylistIdOrderByRank(long playlistId) {
        return playlistRankingsByPlaylistIndex.query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(playlistId)
                        .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    /** Bounded to one tenant's partition — a Query, not a table-wide Scan. */
    public List<PlaylistRankingItem> findAllByTenant(long tenantId) {
        return table.query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(PlaylistRankingItem.partitionKey(tenantId))
                        .build()))
                .items()
                .stream()
                .toList();
    }
}
