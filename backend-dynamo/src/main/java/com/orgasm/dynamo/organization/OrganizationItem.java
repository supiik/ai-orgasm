package com.orgasm.dynamo.organization;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

/**
 * DynamoDB item for an Organization. Not tenant-partitioned — this table IS the tenant
 * directory, so {@code id} is the natural partition key rather than a composed {@code pk}/{@code sk}.
 */
@DynamoDbBean
public class OrganizationItem {

    private Long id;
    private String slug;
    private String name;

    @DynamoDbPartitionKey
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "bySlug")
    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
