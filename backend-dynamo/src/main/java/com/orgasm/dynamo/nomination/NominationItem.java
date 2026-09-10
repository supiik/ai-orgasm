package com.orgasm.dynamo.nomination;

import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

/**
 * DynamoDB item for a Nomination. Partition key is {@code <tenantId>#NOMINATION} so a per-tenant
 * Query (not a table-wide Scan) can list a tenant's nominations cheaply; sort key is the item id.
 * Two GSIs support the access patterns the JPA repository exposed via derived queries: "byPlaylist"
 * (playlistId + songId, used for both findByPlaylistId and the uniqueness existence check) and
 * "bySong" (songId + id, used for the cross-playlist findBySongId lookup).
 */
@DynamoDbBean
public class NominationItem {

    public static String partitionKey(long tenantId) {
        return tenantId + "#NOMINATION";
    }

    private String pk;
    private String sk;
    private Long id;
    private Long tenantId;
    private Long playlistId;
    private Long songId;
    private Long nominatedById;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private Long version;

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

    @DynamoDbSecondarySortKey(indexNames = "bySong")
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

    @DynamoDbSecondarySortKey(indexNames = "byPlaylist")
    @DynamoDbSecondaryPartitionKey(indexNames = "bySong")
    public Long getSongId() {
        return songId;
    }

    public void setSongId(Long songId) {
        this.songId = songId;
    }

    public Long getNominatedById() {
        return nominatedById;
    }

    public void setNominatedById(Long nominatedById) {
        this.nominatedById = nominatedById;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @DynamoDbVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
