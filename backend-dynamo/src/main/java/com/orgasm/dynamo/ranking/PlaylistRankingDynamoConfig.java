package com.orgasm.dynamo.ranking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class PlaylistRankingDynamoConfig {

    @Value("${app.dynamodb.table-playlist-rankings:orgasm-playlist-rankings-local}")
    private String tableName;

    @Bean
    public DynamoDbTable<PlaylistRankingItem> playlistRankingsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(PlaylistRankingItem.class));
    }

    @Bean
    public DynamoDbIndex<PlaylistRankingItem> playlistRankingsByPlaylistIndex(
            DynamoDbTable<PlaylistRankingItem> playlistRankingsTable) {
        return playlistRankingsTable.index("byPlaylist");
    }
}
