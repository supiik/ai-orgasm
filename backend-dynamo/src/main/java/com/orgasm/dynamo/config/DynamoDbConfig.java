package com.orgasm.dynamo.config;

import com.orgasm.dynamo.playlist.PlaylistItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.extensions.VersionedRecordExtension;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Value("${app.dynamodb.endpoint-override:}")
    private String endpointOverride;

    @Value("${app.dynamodb.table-playlists:orgasm-playlists-local}")
    private String playlistsTableName;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        if (!endpointOverride.isBlank()) {
            // Local/test only (DynamoDB Local doesn't validate credentials but the SDK
            // still requires a region + a non-empty credentials provider to build a client).
            builder.endpointOverride(URI.create(endpointOverride))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(
                            StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        // Explicit: needed for @DynamoDbVersionAttribute (optimistic locking) to take effect.
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .extensions(VersionedRecordExtension.builder().build())
                .build();
    }

    @Bean
    public DynamoDbTable<PlaylistItem> playlistsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(playlistsTableName, TableSchema.fromBean(PlaylistItem.class));
    }
}
