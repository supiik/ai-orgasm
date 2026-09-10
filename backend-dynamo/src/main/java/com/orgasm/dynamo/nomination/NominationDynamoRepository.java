package com.orgasm.dynamo.nomination;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NominationDynamoRepository {

    private final DynamoDbTable<NominationItem> table;
    private final DynamoDbIndex<NominationItem> nominationsByPlaylistIndex;
    private final DynamoDbIndex<NominationItem> nominationsBySongIndex;

    /**
     * Uses updateItem (not putItem) because the Enhanced Client only returns the
     * post-write item state — with the version attribute populated — from updateItem;
     * putItem returns void and never mutates the Java object passed to it.
     */
    public NominationItem save(NominationItem item) {
        return table.updateItem(item);
    }

    public Optional<NominationItem> findById(long tenantId, long id) {
        NominationItem item = table.getItem(Key.builder()
                .partitionValue(NominationItem.partitionKey(tenantId))
                .sortValue(String.valueOf(id))
                .build());
        return Optional.ofNullable(item);
    }

    public List<NominationItem> findByPlaylistId(long playlistId) {
        return nominationsByPlaylistIndex.query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(playlistId)
                        .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    public boolean existsByPlaylistIdAndSongId(long playlistId, long songId) {
        return nominationsByPlaylistIndex.query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(playlistId)
                        .sortValue(songId)
                        .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .findAny()
                .isPresent();
    }

    public List<NominationItem> findBySongId(long songId) {
        return nominationsBySongIndex.query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(songId)
                        .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    /**
     * Loads and rewrites each item in full (not a partial ignoreNulls update) because the
     * VersionedRecordExtension needs the item's current version to build its conditional
     * expression — a partial item with no version reads as "doesn't exist yet" and the
     * conditional check fails against an item that already exists.
     */
    public int declinePendingByPlaylistId(long playlistId, Instant now) {
        List<NominationItem> pending = findByPlaylistId(playlistId).stream()
                .filter(item -> NominationStatus.PENDING.name().equals(item.getStatus()))
                .toList();

        for (NominationItem item : pending) {
            item.setStatus(NominationStatus.DECLINED.name());
            item.setUpdatedAt(now);
            table.updateItem(item);
        }

        return pending.size();
    }
}
