package com.orgasm.dynamo.guessing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class GuessSubmissionDynamoConfig {

    @Value("${app.dynamodb.table-guess-submissions:orgasm-guess-submissions-local}")
    private String tableName;

    @Bean
    public DynamoDbTable<GuessSubmissionItem> guessSubmissionsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(GuessSubmissionItem.class));
    }

    @Bean
    public DynamoDbIndex<GuessSubmissionItem> guessSubmissionsByPlaylistIndex(
            DynamoDbTable<GuessSubmissionItem> guessSubmissionsTable) {
        return guessSubmissionsTable.index("byPlaylist");
    }
}
