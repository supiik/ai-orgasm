package com.orgasm.dynamo.guessing;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class GuessDynamoRepositoryIT {

    private static final String TABLE_NAME = "orgasm-guesses-local";

    @Container
    static GenericContainer<?> dynamoDbLocal =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.4")).withExposedPorts(8000);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.endpoint-override", GuessDynamoRepositoryIT::endpoint);
        registry.add("app.dynamodb.table-guesses", () -> TABLE_NAME);
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

    @Autowired GuessDynamoRepository repository;

    private static GuessItem guess(long id, long tenantId, long playlistId, long nominationId, long guesserId, long guessedContributorId) {
        GuessItem item = new GuessItem();
        item.setPk(GuessItem.partitionKey(tenantId));
        item.setSk(String.valueOf(id));
        item.setId(id);
        item.setTenantId(tenantId);
        item.setPlaylistId(playlistId);
        item.setNominationId(nominationId);
        item.setGuesserId(guesserId);
        item.setGuessedContributorId(guessedContributorId);
        item.setCreatedAt(Instant.now());
        return item;
    }

    @Test
    void findByPlaylistId_thenDeleteByPlaylistAndGuesser_scopesCorrectly() {
        repository.save(guess(1L, 1L, 100L, 1L, 10L, 50L));
        repository.save(guess(2L, 1L, 100L, 2L, 10L, 51L));
        repository.save(guess(3L, 1L, 100L, 3L, 20L, 52L));
        repository.save(guess(4L, 1L, 200L, 4L, 10L, 53L));

        List<GuessItem> playlist100 = repository.findByPlaylistId(100L);
        assertThat(playlist100).extracting(GuessItem::getId).containsExactlyInAnyOrder(1L, 2L, 3L);

        List<GuessItem> playlist200 = repository.findByPlaylistId(200L);
        assertThat(playlist200).extracting(GuessItem::getId).containsExactly(4L);

        repository.deleteByPlaylistAndGuesser(100L, 10L);

        List<GuessItem> playlist100AfterDelete = repository.findByPlaylistId(100L);
        assertThat(playlist100AfterDelete).extracting(GuessItem::getId).containsExactly(3L);

        List<GuessItem> playlist200AfterDelete = repository.findByPlaylistId(200L);
        assertThat(playlist200AfterDelete).extracting(GuessItem::getId).containsExactly(4L);
    }
}
