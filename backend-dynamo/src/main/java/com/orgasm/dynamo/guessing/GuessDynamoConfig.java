package com.orgasm.dynamo.guessing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class GuessDynamoConfig {

    @Value("${app.dynamodb.table-guesses:orgasm-guesses-local}")
    private String tableName;

    @Bean
    public DynamoDbTable<GuessItem> guessesTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(GuessItem.class));
    }

    @Bean
    public DynamoDbIndex<GuessItem> guessesByPlaylistIndex(DynamoDbTable<GuessItem> guessesTable) {
        return guessesTable.index("byPlaylist");
    }
}
