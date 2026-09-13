package com.orgasm.dynamo.song;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class SongDynamoConfig {

    @Value("${app.dynamodb.table-songs:orgasm-songs-local}")
    private String tableName;

    @Bean
    public DynamoDbTable<SongItem> songsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(SongItem.class));
    }
}
