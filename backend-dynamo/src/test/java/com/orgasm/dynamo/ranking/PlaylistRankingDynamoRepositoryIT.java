package com.orgasm.dynamo.ranking;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class PlaylistRankingDynamoRepositoryIT {

    private static final String TABLE_NAME = "orgasm-playlist-rankings-local";

    @Container
    static GenericContainer<?> dynamoDbLocal =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.4")).withExposedPorts(8000);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.endpoint-override", PlaylistRankingDynamoRepositoryIT::endpoint);
        registry.add("app.dynamodb.table-playlist-rankings", () -> TABLE_NAME);
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
                            AttributeDefinition.builder().attributeName("rankPosition").attributeType(ScalarAttributeType.N).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName("byPlaylist")
                            .keySchema(
                                    KeySchemaElement.builder().attributeName("playlistId").keyType(KeyType.HASH).build(),
                                    KeySchemaElement.builder().attributeName("rankPosition").keyType(KeyType.RANGE).build())
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build())
                    .build());
        }
    }

    private static String endpoint() {
        return "http://" + dynamoDbLocal.getHost() + ":" + dynamoDbLocal.getMappedPort(8000);
    }

    @Autowired PlaylistRankingDynamoRepository repository;

    private static PlaylistRankingItem newItem(long tenantId, long id, long playlistId, long contributorId, int rankPosition) {
        PlaylistRankingItem item = new PlaylistRankingItem();
        item.setPk(PlaylistRankingItem.partitionKey(tenantId));
        item.setSk(String.valueOf(id));
        item.setId(id);
        item.setTenantId(tenantId);
        item.setPlaylistId(playlistId);
        item.setContributorId(contributorId);
        item.setRankPosition(rankPosition);
        item.setCorrectGuesses(rankPosition);
        item.setTotalGuesses(10);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    @Test
    void findByPlaylistIdOrderByRank_returnsAscendingByRankPosition() {
        repository.save(newItem(1L, 1L, 500L, 1L, 3));
        repository.save(newItem(1L, 2L, 500L, 2L, 1));
        repository.save(newItem(1L, 3L, 500L, 3L, 2));

        var rankings = repository.findByPlaylistIdOrderByRank(500L);

        assertThat(rankings)
                .extracting(PlaylistRankingItem::getRankPosition)
                .containsExactly(1, 2, 3);
    }

    @Test
    void findAllByTenant_returnsRankingsAcrossAllPlaylistsForTenant() {
        repository.save(newItem(2L, 4L, 600L, 1L, 1));
        repository.save(newItem(2L, 5L, 700L, 2L, 1));
        repository.save(newItem(3L, 6L, 800L, 1L, 1));

        var rankings = repository.findAllByTenant(2L);

        assertThat(rankings)
                .extracting(PlaylistRankingItem::getPlaylistId)
                .containsExactlyInAnyOrder(600L, 700L);
    }
}
