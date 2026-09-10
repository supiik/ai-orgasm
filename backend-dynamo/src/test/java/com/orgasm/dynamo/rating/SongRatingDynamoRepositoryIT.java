package com.orgasm.dynamo.rating;

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
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SongRatingDynamoRepositoryIT {

    private static final String TABLE_NAME = "orgasm-song-ratings-local";

    @Container
    static GenericContainer<?> dynamoDbLocal =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.4")).withExposedPorts(8000);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.endpoint-override", SongRatingDynamoRepositoryIT::endpoint);
        registry.add("app.dynamodb.table-song-ratings", () -> TABLE_NAME);
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
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.N).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName("byPlaylist")
                            .keySchema(
                                    KeySchemaElement.builder().attributeName("playlistId").keyType(KeyType.HASH).build(),
                                    KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build())
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build())
                    .build());
        }
    }

    private static String endpoint() {
        return "http://" + dynamoDbLocal.getHost() + ":" + dynamoDbLocal.getMappedPort(8000);
    }

    @Autowired SongRatingDynamoRepository repository;

    private static SongRatingItem newItem(long tenantId, long id, long playlistId, long contributorId, long nominationId, int points) {
        SongRatingItem item = new SongRatingItem();
        item.setPk(SongRatingItem.partitionKey(tenantId));
        item.setSk(String.valueOf(id));
        item.setId(id);
        item.setTenantId(tenantId);
        item.setPlaylistId(playlistId);
        item.setContributorId(contributorId);
        item.setNominationId(nominationId);
        item.setPoints(points);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    @Test
    void findByPlaylistId_returnsOnlyThatPlaylistsRatings() {
        repository.save(newItem(1L, 1L, 100L, 1L, 10L, 5));
        repository.save(newItem(1L, 2L, 100L, 2L, 11L, 3));
        repository.save(newItem(1L, 3L, 200L, 1L, 12L, 4));

        var ratings = repository.findByPlaylistId(100L);

        assertThat(ratings)
                .extracting(SongRatingItem::getContributorId, SongRatingItem::getPoints)
                .containsExactlyInAnyOrder(tuple(1L, 5), tuple(2L, 3));
    }

    @Test
    void deleteByPlaylistAndContributor_removesOnlyMatchingRows() {
        repository.save(newItem(2L, 4L, 300L, 1L, 20L, 5));
        repository.save(newItem(2L, 5L, 300L, 2L, 21L, 3));
        repository.save(newItem(2L, 6L, 400L, 1L, 22L, 2));

        repository.deleteByPlaylistAndContributor(300L, 1L);

        assertThat(repository.findByPlaylistId(300L))
                .extracting(SongRatingItem::getContributorId)
                .containsExactly(2L);
        assertThat(repository.findByPlaylistId(400L))
                .extracting(SongRatingItem::getContributorId)
                .containsExactly(1L);
    }
}
