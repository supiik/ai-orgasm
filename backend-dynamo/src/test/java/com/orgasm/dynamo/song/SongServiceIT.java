package com.orgasm.dynamo.song;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class SongServiceIT {

    private static final String TABLE_NAME = "orgasm-songs-local";

    @Container
    static GenericContainer<?> dynamoDbLocal =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.4")).withExposedPorts(8000);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.endpoint-override", SongServiceIT::endpoint);
        registry.add("app.dynamodb.table-songs", () -> TABLE_NAME);
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
                            AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                    .build());
        }
    }

    private static String endpoint() {
        return "http://" + dynamoDbLocal.getHost() + ":" + dynamoDbLocal.getMappedPort(8000);
    }

    @Autowired SongService service;

    @Test
    void create_thenFindById_roundTrips() {
        var created = service.create(new CreateSongRequest("Some Artist", "My Song", "My Album", 2020, "http://example.com/song"));

        assertThat(created.id()).isNotBlank();
        assertThat(created.version()).isEqualTo(1L);

        var found = service.findById(created.id());

        assertThat(found).isPresent();
        assertThat(found.get().artist()).isEqualTo("Some Artist");
        assertThat(found.get().name()).isEqualTo("My Song");
        assertThat(found.get().album()).isEqualTo("My Album");
        assertThat(found.get().releaseYear()).isEqualTo(2020);
        assertThat(found.get().url()).isEqualTo("http://example.com/song");
    }

    @Test
    void findById_returnsEmpty_whenMissing() {
        assertThat(service.findById("song-0000000000000000")).isEmpty();
    }

    @Test
    void findAll_returnsCreatedSongs() {
        service.create(new CreateSongRequest("Road Artist", "Roadtrip Song", null, null, null));

        Page<SongResponse> page = service.findAll(r -> r.name("Roadtrip"), Pageable.ofSize(10));

        assertThat(page.getContent()).anySatisfy(s -> assertThat(s.name()).isEqualTo("Roadtrip Song"));
    }

    @Test
    void update_thenDelete_roundTrips() {
        var created = service.create(new CreateSongRequest("Some Artist", "My Song", "My Album", 2020, "http://example.com/song"));

        var updated = service.update(created.id(), new UpdateSongRequest("New Artist", "New Song", "New Album", 2021, "http://example.com/new"));

        assertThat(updated.artist()).isEqualTo("New Artist");
        assertThat(updated.name()).isEqualTo("New Song");
        assertThat(updated.album()).isEqualTo("New Album");
        assertThat(updated.releaseYear()).isEqualTo(2021);
        assertThat(updated.url()).isEqualTo("http://example.com/new");
        assertThat(updated.version()).isEqualTo(2L);

        service.delete(created.id());

        assertThat(service.findById(created.id())).isEmpty();
    }
}
