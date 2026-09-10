package com.orgasm.dynamo.guessing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
@RequiredArgsConstructor
public class GuessSubmissionDynamoRepository {

    private final DynamoDbTable<GuessSubmissionItem> table;
    private final DynamoDbIndex<GuessSubmissionItem> guessSubmissionsByPlaylistIndex;

    /**
     * Uses updateItem (not putItem) because the Enhanced Client only returns the
     * post-write item state — with the version attribute populated — from updateItem;
     * putItem returns void and never mutates the Java object passed to it.
     */
    public GuessSubmissionItem save(GuessSubmissionItem item) {
        return table.updateItem(item);
    }

    public boolean existsByPlaylistIdAndContributorId(long playlistId, long contributorId) {
        return guessSubmissionsByPlaylistIndex
                .query(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(playlistId)
                        .sortValue(contributorId)
                        .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .findAny()
                .isPresent();
    }
}
