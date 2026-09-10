package com.orgasm.dynamo.organization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Configuration
public class OrganizationDynamoConfig {

    @Value("${app.dynamodb.table-organizations:orgasm-organizations-local}")
    private String tableName;

    @Bean
    public DynamoDbTable<OrganizationItem> organizationsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(OrganizationItem.class));
    }

    @Bean
    public DynamoDbIndex<OrganizationItem> organizationsBySlugIndex(DynamoDbTable<OrganizationItem> organizationsTable) {
        return organizationsTable.index("bySlug");
    }
}
