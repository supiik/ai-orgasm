package com.orgasm.dynamo.contributor;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the byCognitoSub GSI, not covered by {@link ContributorServiceIT}'s simpler pk/sk-only table. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ContributorDynamoRepositoryIT {

    private static final String TABLE_NAME = "orgasm-contributors-gsi-it";

    @Container
    static GenericContainer<?> dynamoDbLocal =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.4")).withExposedPorts(8000);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.endpoint-override", ContributorDynamoRepositoryIT::endpoint);
        registry.add("app.dynamodb.table-contributors", () -> TABLE_NAME);
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
                            AttributeDefinition.builder().attributeName("cognitoSub").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName("byCognitoSub")
                            .keySchema(KeySchemaElement.builder().attributeName("cognitoSub").keyType(KeyType.HASH).build())
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build())
                    .build());
        }
    }

    private static String endpoint() {
        return "http://" + dynamoDbLocal.getHost() + ":" + dynamoDbLocal.getMappedPort(8000);
    }

    @Autowired ContributorDynamoRepository repository;

    private static ContributorItem newItem(long tenantId, long id, String name, String email) {
        ContributorItem item = new ContributorItem();
        item.setPk(ContributorItem.partitionKey(tenantId));
        item.setSk(String.valueOf(id));
        item.setId(id);
        item.setTenantId(tenantId);
        item.setName(name);
        item.setEmail(email);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    @Test
    void findByCognitoSub_returnsEmpty_whenNoMatch() {
        assertThat(repository.findByCognitoSub("no-such-sub")).isEmpty();
    }

    @Test
    void findByCognitoSub_findsLinkedContributor() {
        var item = newItem(1L, 101L, "Ada", "ada@example.com");
        item.setCognitoSub("sub-ada");
        repository.save(item);

        Optional<ContributorItem> found = repository.findByCognitoSub("sub-ada");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Ada");
    }

    @Test
    void findByEmail_matchesCaseInsensitively_withinTenant() {
        repository.save(newItem(2L, 201L, "Bob", "Bob@Example.com"));

        assertThat(repository.findByEmail(2L, "bob@example.com")).isPresent();
        assertThat(repository.findByEmail(2L, "nobody@example.com")).isEmpty();
        assertThat(repository.findByEmail(3L, "bob@example.com")).isEmpty(); // different tenant
    }
}
