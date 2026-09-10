package com.orgasm.dynamo.orgasm;

import com.orgasm.dynamo.contributor.ContributorService;
import com.orgasm.dynamo.contributor.CreateContributorRequest;
import com.orgasm.dynamo.nomination.NominationStatus;
import com.orgasm.dynamo.playlist.CreatePlaylistRequest;
import com.orgasm.dynamo.playlist.PlaylistService;
import com.orgasm.dynamo.playlist.PlaylistStatus;
import com.orgasm.dynamo.playlist.RatingType;
import com.orgasm.dynamo.song.CreateSongRequest;
import com.orgasm.dynamo.song.SongService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * Exercises the full Orgasm workflow end-to-end against real DynamoDB Local, across all 8
 * tables: open -> nominate -> approve -> start-guessing -> submit-guesses -> publish -> rankings
 * -> submit-ratings -> ratings. The interesting bugs in a port like this live in the state
 * transitions between steps, so one broad happy-path test here is worth more than testing each
 * step in isolation (individual validation rules are covered by OrgasmServiceTest's mocked unit
 * tests instead).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class OrgasmServiceIT {

    @Container
    static GenericContainer<?> dynamoDbLocal =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.4")).withExposedPorts(8000);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.endpoint-override", OrgasmServiceIT::endpoint);
    }

    private static String endpoint() {
        return "http://" + dynamoDbLocal.getHost() + ":" + dynamoDbLocal.getMappedPort(8000);
    }

    @BeforeAll
    static void createTables() {
        try (DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build()) {
            createSimpleTable(client, "orgasm-playlists-local");
            createSimpleTable(client, "orgasm-songs-local");
            createSimpleTable(client, "orgasm-contributors-local");

            createTableWithGsi(client, "orgasm-nominations-local", List.of(
                    gsi("byPlaylist", "playlistId", ScalarAttributeType.N, "songId", ScalarAttributeType.N),
                    gsi("bySong", "songId", ScalarAttributeType.N, "id", ScalarAttributeType.N)));
            createTableWithGsi(client, "orgasm-guesses-local", List.of(
                    gsi("byPlaylist", "playlistId", ScalarAttributeType.N, "id", ScalarAttributeType.N)));
            createTableWithGsi(client, "orgasm-guess-submissions-local", List.of(
                    gsi("byPlaylist", "playlistId", ScalarAttributeType.N, "contributorId", ScalarAttributeType.N)));
            createTableWithGsi(client, "orgasm-song-ratings-local", List.of(
                    gsi("byPlaylist", "playlistId", ScalarAttributeType.N, "id", ScalarAttributeType.N)));
            createTableWithGsi(client, "orgasm-playlist-rankings-local", List.of(
                    gsi("byPlaylist", "playlistId", ScalarAttributeType.N, "rankPosition", ScalarAttributeType.N)));
        }
    }

    private static void createSimpleTable(DynamoDbClient client, String tableName) {
        client.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .keySchema(
                        KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                .build());
    }

    private record GsiSpec(String name, String partitionAttr, ScalarAttributeType partitionType,
                            String sortAttr, ScalarAttributeType sortType) {}

    private static GsiSpec gsi(String name, String partitionAttr, ScalarAttributeType partitionType,
                                String sortAttr, ScalarAttributeType sortType) {
        return new GsiSpec(name, partitionAttr, partitionType, sortAttr, sortType);
    }

    private static void createTableWithGsi(DynamoDbClient client, String tableName, List<GsiSpec> gsis) {
        var attributeDefinitions = new java.util.LinkedHashMap<String, ScalarAttributeType>();
        attributeDefinitions.put("pk", ScalarAttributeType.S);
        attributeDefinitions.put("sk", ScalarAttributeType.S);
        for (GsiSpec spec : gsis) {
            attributeDefinitions.put(spec.partitionAttr(), spec.partitionType());
            attributeDefinitions.put(spec.sortAttr(), spec.sortType());
        }

        var globalSecondaryIndexes = gsis.stream()
                .map(spec -> GlobalSecondaryIndex.builder()
                        .indexName(spec.name())
                        .keySchema(
                                KeySchemaElement.builder().attributeName(spec.partitionAttr()).keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName(spec.sortAttr()).keyType(KeyType.RANGE).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                .toList();

        client.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attributeDefinitions.entrySet().stream()
                        .map(e -> AttributeDefinition.builder().attributeName(e.getKey()).attributeType(e.getValue()).build())
                        .toList())
                .keySchema(
                        KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                .globalSecondaryIndexes(globalSecondaryIndexes)
                .build());
    }

    @Autowired PlaylistService playlistService;
    @Autowired ContributorService contributorService;
    @Autowired SongService songService;
    @Autowired OrgasmService orgasmService;

    @Test
    void fullWorkflow_openThroughRatings() {
        var lead = contributorService.create(CreateContributorRequest.builder().name("Lead").build());
        var guesser1 = contributorService.create(CreateContributorRequest.builder().name("Guesser1").build());
        var guesser2 = contributorService.create(CreateContributorRequest.builder().name("Guesser2").build());

        var song1 = songService.create(CreateSongRequest.builder().artist("Artist A").name("Song A").build());
        var song2 = songService.create(CreateSongRequest.builder().artist("Artist B").name("Song B").build());

        var playlist = playlistService.create(CreatePlaylistRequest.builder()
                .name("Test Mix")
                .ratingType(RatingType.BEST_SONG)
                .build());

        var opened = orgasmService.openPlaylist(playlist.id(), OpenPlaylistRequest.builder()
                .contributorId(lead.id())
                .deadline(Instant.now().plusSeconds(3600))
                .build());
        assertThat(opened.status()).isEqualTo(PlaylistStatus.OPEN);
        assertThat(opened.leadContributorId()).isEqualTo(lead.id());
        assertThat(opened.leadContributorName()).isEqualTo("Lead");

        var nomination1 = orgasmService.nominateSong(playlist.id(), NominateSongRequest.builder()
                .contributorId(guesser1.id())
                .songId(song1.id())
                .build());
        var nomination2 = orgasmService.nominateSong(playlist.id(), NominateSongRequest.builder()
                .contributorId(guesser2.id())
                .songId(song2.id())
                .build());
        assertThat(nomination1.status()).isEqualTo(NominationStatus.PENDING);

        assertThat(orgasmService.findNominations(playlist.id(), Pageable.unpaged()).getContent()).hasSize(2);
        assertThat(orgasmService.findNominationsBySong(song1.id())).hasSize(1);

        orgasmService.approveNomination(nomination1.id(), ReviewNominationRequest.builder().reviewerId(lead.id()).build());
        orgasmService.approveNomination(nomination2.id(), ReviewNominationRequest.builder().reviewerId(lead.id()).build());

        var guessingStarted = orgasmService.startGuessing(playlist.id(),
                StartGuessingRequest.builder().contributorId(lead.id()).build());
        assertThat(guessingStarted.status()).isEqualTo(PlaylistStatus.GUESSING);
        assertThat(guessingStarted.guessingDeadline()).isNotNull();

        // guesser1 gets both right
        orgasmService.submitGuesses(playlist.id(), SubmitGuessesRequest.builder()
                .contributorId(guesser1.id())
                .guesses(List.of(
                        SubmitGuessesRequest.GuessSelection.builder()
                                .nominationId(nomination1.id()).guessedContributorId(guesser1.id()).build(),
                        SubmitGuessesRequest.GuessSelection.builder()
                                .nominationId(nomination2.id()).guessedContributorId(guesser2.id()).build()))
                .build());

        // guesser2 gets both wrong
        orgasmService.submitGuesses(playlist.id(), SubmitGuessesRequest.builder()
                .contributorId(guesser2.id())
                .guesses(List.of(
                        SubmitGuessesRequest.GuessSelection.builder()
                                .nominationId(nomination1.id()).guessedContributorId(guesser2.id()).build(),
                        SubmitGuessesRequest.GuessSelection.builder()
                                .nominationId(nomination2.id()).guessedContributorId(guesser1.id()).build()))
                .build());

        assertThat(orgasmService.getGuesses(playlist.id())).hasSize(4);

        var published = orgasmService.publishPlaylist(playlist.id(),
                PublishPlaylistRequest.builder().contributorId(lead.id()).build());
        assertThat(published.status()).isEqualTo(PlaylistStatus.PUBLISHED);

        var rankings = orgasmService.getRankings();
        assertThat(rankings).hasSize(2);
        var byContributor = rankings.stream()
                .collect(java.util.stream.Collectors.toMap(RankingResponse::contributorId, r -> r));
        assertThat(byContributor.get(guesser1.id()).correctGuesses()).isEqualTo(2);
        assertThat(byContributor.get(guesser1.id()).rankPosition()).isEqualTo(1);
        assertThat(byContributor.get(guesser2.id()).correctGuesses()).isEqualTo(0);
        assertThat(byContributor.get(guesser2.id()).rankPosition()).isEqualTo(2);

        // guesser1 rates guesser2's nomination (not their own) with the single BEST_SONG point
        orgasmService.submitRatings(playlist.id(), SubmitRatingsRequest.builder()
                .contributorId(guesser1.id())
                .ratings(List.of(SubmitRatingsRequest.RatingEntry.builder()
                        .nominationId(nomination2.id())
                        .points(1)
                        .build()))
                .build());

        var songRatings = orgasmService.getSongRatings(playlist.id());
        assertThat(songRatings).hasSize(1);
        assertThat(songRatings.get(0).nominationId()).isEqualTo(nomination2.id());
        assertThat(songRatings.get(0).contributorId()).isEqualTo(guesser1.id());
        assertThat(songRatings.get(0).points()).isEqualTo(1);

        assertThat(orgasmService.findPlaylistsByContributor(lead.id(), Pageable.unpaged()).getContent()).hasSize(1);
    }
}
