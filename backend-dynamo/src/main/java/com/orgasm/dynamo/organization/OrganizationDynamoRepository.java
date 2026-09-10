package com.orgasm.dynamo.organization;

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
public class OrganizationDynamoRepository {

    private final DynamoDbTable<OrganizationItem> table;
    private final DynamoDbIndex<OrganizationItem> organizationsBySlugIndex;

    /** No version attribute on this item, so putItem's void-return limitation doesn't matter. */
    public OrganizationItem save(OrganizationItem item) {
        table.putItem(item);
        return item;
    }

    /** Small/global tenant-directory table — a table-wide Scan matches the real app's unpaged findAll(). */
    public List<OrganizationItem> findAll() {
        return table.scan().items().stream().toList();
    }

    /** Assumes slug is unique by convention; DynamoDB does not enforce it for a GSI partition key. */
    public Optional<OrganizationItem> findBySlug(String slug) {
        return organizationsBySlugIndex
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(slug).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }
}
