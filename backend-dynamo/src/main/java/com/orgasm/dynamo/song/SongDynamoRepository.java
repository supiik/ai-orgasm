package com.orgasm.dynamo.song;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SongDynamoRepository {

    private final DynamoDbTable<SongItem> table;

    /**
     * Uses updateItem (not putItem) because the Enhanced Client only returns the
     * post-write item state — with the version attribute populated — from updateItem;
     * putItem returns void and never mutates the Java object passed to it.
     */
    public SongItem save(SongItem item) {
        return table.updateItem(item);
    }

    public Optional<SongItem> findById(long tenantId, long id) {
        SongItem item = table.getItem(Key.builder()
                .partitionValue(SongItem.partitionKey(tenantId))
                .sortValue(String.valueOf(id))
                .build());
        return Optional.ofNullable(item);
    }

    /** Bounded to one tenant's partition — a Query, not a table-wide Scan. */
    public List<SongItem> findAllByTenant(long tenantId) {
        return table.query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(SongItem.partitionKey(tenantId))
                        .build()))
                .items()
                .stream()
                .toList();
    }
}
