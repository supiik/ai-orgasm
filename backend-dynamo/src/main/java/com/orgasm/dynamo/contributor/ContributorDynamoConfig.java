package com.orgasm.dynamo.contributor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class ContributorDynamoConfig {

    @Value("${app.dynamodb.table-contributors:orgasm-contributors-local}")
    private String tableName;

    @Bean
    public DynamoDbTable<ContributorItem> contributorsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(ContributorItem.class));
    }

    @Bean
    public DynamoDbIndex<ContributorItem> contributorsByCognitoSubIndex(DynamoDbTable<ContributorItem> contributorsTable) {
        return contributorsTable.index("byCognitoSub");
    }
}
