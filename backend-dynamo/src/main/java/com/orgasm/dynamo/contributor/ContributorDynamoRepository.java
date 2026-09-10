package com.orgasm.dynamo.contributor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ContributorDynamoRepository {

    private final DynamoDbTable<ContributorItem> table;
    private final DynamoDbIndex<ContributorItem> contributorsByCognitoSubIndex;

    /**
     * Uses updateItem (not putItem) because the Enhanced Client only returns the
     * post-write item state — with the version attribute populated — from updateItem;
     * putItem returns void and never mutates the Java object passed to it.
     */
    public ContributorItem save(ContributorItem item) {
        return table.updateItem(item);
    }

    public Optional<ContributorItem> findById(long tenantId, long id) {
        ContributorItem item = table.getItem(Key.builder()
                .partitionValue(ContributorItem.partitionKey(tenantId))
                .sortValue(String.valueOf(id))
                .build());
        return Optional.ofNullable(item);
    }

    /** Bounded to one tenant's partition — a Query, not a table-wide Scan. */
    public List<ContributorItem> findAllByTenant(long tenantId) {
        return table.query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(ContributorItem.partitionKey(tenantId))
                        .build()))
                .items()
                .stream()
                .toList();
    }

    /** Assumes cognitoSub is unique by convention; DynamoDB does not enforce it for a GSI partition key. */
    public Optional<ContributorItem> findByCognitoSub(String cognitoSub) {
        return contributorsByCognitoSubIndex
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(cognitoSub).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    /** In-memory filter over the tenant's already-fetched Query result — no GSI needed at this table's scale. */
    public Optional<ContributorItem> findByEmail(long tenantId, String email) {
        return findAllByTenant(tenantId).stream()
                .filter(item -> email.equalsIgnoreCase(item.getEmail()))
                .findFirst();
    }
}
