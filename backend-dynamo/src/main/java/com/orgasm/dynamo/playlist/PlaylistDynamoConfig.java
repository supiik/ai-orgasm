package com.orgasm.dynamo.playlist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class PlaylistDynamoConfig {

    @Value("${app.dynamodb.table-playlists:orgasm-playlists-local}")
    private String tableName;

    @Bean
    public DynamoDbTable<PlaylistItem> playlistsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(PlaylistItem.class));
    }
}
