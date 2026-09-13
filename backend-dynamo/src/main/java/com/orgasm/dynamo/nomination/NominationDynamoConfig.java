package com.orgasm.dynamo.nomination;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class NominationDynamoConfig {

    @Value("${app.dynamodb.table-nominations:orgasm-nominations-local}")
    private String tableName;

    @Bean
    public DynamoDbTable<NominationItem> nominationsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(NominationItem.class));
    }

    @Bean
    public DynamoDbIndex<NominationItem> nominationsByPlaylistIndex(DynamoDbTable<NominationItem> nominationsTable) {
        return nominationsTable.index("byPlaylist");
    }

    @Bean
    public DynamoDbIndex<NominationItem> nominationsBySongIndex(DynamoDbTable<NominationItem> nominationsTable) {
        return nominationsTable.index("bySong");
    }
}
