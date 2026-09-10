package com.orgasm.dynamo.rating;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class SongRatingDynamoConfig {

    @Value("${app.dynamodb.table-song-ratings:orgasm-song-ratings-local}")
    private String tableName;

    @Bean
    public DynamoDbTable<SongRatingItem> songRatingsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(SongRatingItem.class));
    }

    @Bean
    public DynamoDbIndex<SongRatingItem> songRatingsByPlaylistIndex(DynamoDbTable<SongRatingItem> songRatingsTable) {
        return songRatingsTable.index("byPlaylist");
    }
}
