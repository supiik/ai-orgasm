package com.orgasm.dynamo.guessing;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

/**
 * DynamoDB item for a Guess. Partition key is {@code <tenantId>#GUESS} so a per-tenant
 * Query (not a table-wide Scan) can list a tenant's guesses cheaply; sort key is the item id.
 * Hard-deleted only (no {@code deletedAt}) and never updated in place (no version attribute).
 */
@DynamoDbBean
public class GuessItem {

    public static String partitionKey(long tenantId) {
        return tenantId + "#GUESS";
    }

    private String pk;
    private String sk;
    private Long id;
    private Long tenantId;
    private Long playlistId;
    private Long nominationId;
    private Long guesserId;
    private Long guessedContributorId;
    private Instant createdAt;

    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    @DynamoDbSecondarySortKey(indexNames = "byPlaylist")
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "byPlaylist")
    public Long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(Long playlistId) {
        this.playlistId = playlistId;
    }

    public Long getNominationId() {
        return nominationId;
    }

    public void setNominationId(Long nominationId) {
        this.nominationId = nominationId;
    }

    public Long getGuesserId() {
        return guesserId;
    }

    public void setGuesserId(Long guesserId) {
        this.guesserId = guesserId;
    }

    public Long getGuessedContributorId() {
        return guessedContributorId;
    }

    public void setGuessedContributorId(Long guessedContributorId) {
        this.guessedContributorId = guessedContributorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
