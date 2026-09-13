package com.orgasm.dynamo.guessing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class GuessDynamoRepository {

    private final DynamoDbTable<GuessItem> table;
    private final DynamoDbIndex<GuessItem> guessesByPlaylistIndex;

    /** No version attribute on this item, so putItem's void-return limitation doesn't matter. */
    public GuessItem save(GuessItem item) {
        table.putItem(item);
        return item;
    }

    public List<GuessItem> findByPlaylistId(long playlistId) {
        return guessesByPlaylistIndex
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(playlistId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    /** Full-replace semantics: deletes every guess this guesser recorded for the playlist. */
    public void deleteByPlaylistAndGuesser(long playlistId, long guesserId) {
        findByPlaylistId(playlistId).stream()
                .filter(item -> item.getGuesserId() == guesserId)
                .forEach(item -> table.deleteItem(Key.builder()
                        .partitionValue(GuessItem.partitionKey(item.getTenantId()))
                        .sortValue(String.valueOf(item.getId()))
                        .build()));
    }
}
