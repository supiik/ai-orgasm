package com.orgasm.dynamo.nomination;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class NominationDynamoRepositoryIT {

    private static final String TABLE_NAME = "orgasm-nominations-local";

    @Container
    static GenericContainer<?> dynamoDbLocal =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.4")).withExposedPorts(8000);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.endpoint-override", NominationDynamoRepositoryIT::endpoint);
        registry.add("app.dynamodb.table-nominations", () -> TABLE_NAME);
    }

    @BeforeAll
    static void createTable() {
        try (DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build()) {
            client.createTable(CreateTableRequest.builder()
                    .tableName(TABLE_NAME)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("playlistId").attributeType(ScalarAttributeType.N).build(),
                            AttributeDefinition.builder().attributeName("songId").attributeType(ScalarAttributeType.N).build(),
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.N).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                    .indexName("byPlaylist")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("playlistId").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("songId").keyType(KeyType.RANGE).build())
                                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                    .build(),
                            GlobalSecondaryIndex.builder()
                                    .indexName("bySong")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("songId").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build())
                                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                    .build())
                    .build());
        }
    }

    private static String endpoint() {
        return "http://" + dynamoDbLocal.getHost() + ":" + dynamoDbLocal.getMappedPort(8000);
    }

    @Autowired NominationDynamoRepository repository;

    private static long nextId = 1;

    private NominationItem newItem(long tenantId, long playlistId, long songId, long nominatedById, NominationStatus status) {
        long id = nextId++;
        NominationItem item = new NominationItem();
        item.setPk(NominationItem.partitionKey(tenantId));
        item.setSk(String.valueOf(id));
        item.setId(id);
        item.setTenantId(tenantId);
        item.setPlaylistId(playlistId);
        item.setSongId(songId);
        item.setNominatedById(nominatedById);
        item.setStatus(status.name());
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    @Test
    void findByPlaylistId_returnsOnlyMatchingPlaylist() {
        long tenantId = 1L;
        long playlistA = 100L;
        long playlistB = 200L;

        NominationItem a1 = repository.save(newItem(tenantId, playlistA, 10L, 1L, NominationStatus.PENDING));
        NominationItem a2 = repository.save(newItem(tenantId, playlistA, 11L, 1L, NominationStatus.PENDING));
        repository.save(newItem(tenantId, playlistB, 12L, 1L, NominationStatus.PENDING));

        List<NominationItem> found = repository.findByPlaylistId(playlistA);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(NominationItem::getSongId).containsExactlyInAnyOrder(a1.getSongId(), a2.getSongId());
    }

    @Test
    void existsByPlaylistIdAndSongId_trueAndFalseCases() {
        long tenantId = 1L;
        long playlistId = 300L;
        long songId = 20L;

        repository.save(newItem(tenantId, playlistId, songId, 1L, NominationStatus.PENDING));

        assertThat(repository.existsByPlaylistIdAndSongId(playlistId, songId)).isTrue();
        assertThat(repository.existsByPlaylistIdAndSongId(playlistId, 9999L)).isFalse();
    }

    @Test
    void findBySongId_returnsNominationsAcrossPlaylists() {
        long tenantId = 1L;
        long songId = 30L;

        NominationItem n1 = repository.save(newItem(tenantId, 400L, songId, 1L, NominationStatus.PENDING));
        NominationItem n2 = repository.save(newItem(tenantId, 401L, songId, 2L, NominationStatus.APPROVED));
        repository.save(newItem(tenantId, 402L, 31L, 1L, NominationStatus.PENDING));

        List<NominationItem> found = repository.findBySongId(songId);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(NominationItem::getId).containsExactlyInAnyOrder(n1.getId(), n2.getId());
    }

    @Test
    void declinePendingByPlaylistId_onlyFlipsPendingOnes() {
        long tenantId = 1L;
        long playlistId = 500L;

        NominationItem pending1 = repository.save(newItem(tenantId, playlistId, 40L, 1L, NominationStatus.PENDING));
        NominationItem pending2 = repository.save(newItem(tenantId, playlistId, 41L, 1L, NominationStatus.PENDING));
        NominationItem approved = repository.save(newItem(tenantId, playlistId, 42L, 1L, NominationStatus.APPROVED));

        int updated = repository.declinePendingByPlaylistId(playlistId, Instant.now());

        assertThat(updated).isEqualTo(2);

        List<NominationItem> after = repository.findByPlaylistId(playlistId);
        assertThat(after).filteredOn(item -> item.getId().equals(pending1.getId()) || item.getId().equals(pending2.getId()))
                .allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(NominationStatus.DECLINED.name()));
        assertThat(after).filteredOn(item -> item.getId().equals(approved.getId()))
                .allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(NominationStatus.APPROVED.name()));
    }
}
