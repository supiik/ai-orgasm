import { RemovalPolicy, Stack } from 'aws-cdk-lib'
import { AttributeType, BillingMode, ProjectionType, Table } from 'aws-cdk-lib/aws-dynamodb'

/**
 * The 9 DynamoDB tables backing the Lambda API, ported verbatim (key schema + GSIs) from the
 * former `lambda/template.yaml` (SAM). Raw CDK L2 constructs, not Gen 2's opinionated
 * `defineData`/AppSync model — this schema is hand-rolled pk/sk + GSIs, unrelated to GraphQL.
 * See CLAUDE.md "DynamoDB persistence for Lambda" for the table-design rationale.
 */
export function createTables(stack: Stack, envName: string) {
  const simple = (id: string, name: string) =>
    new Table(stack, id, {
      tableName: `orgasm-${name}-${envName}`,
      billingMode: BillingMode.PAY_PER_REQUEST,
      partitionKey: { name: 'pk', type: AttributeType.STRING },
      sortKey: { name: 'sk', type: AttributeType.STRING },
      removalPolicy: RemovalPolicy.RETAIN,
    })

  const playlists = simple('PlaylistsTable', 'playlists')
  const songs = simple('SongsTable', 'songs')

  const contributors = simple('ContributorsTable', 'contributors')
  contributors.addGlobalSecondaryIndex({
    indexName: 'byCognitoSub',
    partitionKey: { name: 'cognitoSub', type: AttributeType.STRING },
    projectionType: ProjectionType.ALL,
  })

  // Not tenant-partitioned — this table IS the tenant directory (mirrors the separate,
  // multi-tenancy-free "billing" database the real backend keeps Organization in).
  const organizations = new Table(stack, 'OrganizationsTable', {
    tableName: `orgasm-organizations-${envName}`,
    billingMode: BillingMode.PAY_PER_REQUEST,
    partitionKey: { name: 'id', type: AttributeType.NUMBER },
    removalPolicy: RemovalPolicy.RETAIN,
  })
  organizations.addGlobalSecondaryIndex({
    indexName: 'bySlug',
    partitionKey: { name: 'slug', type: AttributeType.STRING },
    projectionType: ProjectionType.ALL,
  })

  const nominations = simple('NominationsTable', 'nominations')
  nominations.addGlobalSecondaryIndex({
    indexName: 'byPlaylist',
    partitionKey: { name: 'playlistId', type: AttributeType.NUMBER },
    sortKey: { name: 'songId', type: AttributeType.NUMBER },
    projectionType: ProjectionType.ALL,
  })
  nominations.addGlobalSecondaryIndex({
    indexName: 'bySong',
    partitionKey: { name: 'songId', type: AttributeType.NUMBER },
    sortKey: { name: 'id', type: AttributeType.NUMBER },
    projectionType: ProjectionType.ALL,
  })

  const guesses = simple('GuessesTable', 'guesses')
  guesses.addGlobalSecondaryIndex({
    indexName: 'byPlaylist',
    partitionKey: { name: 'playlistId', type: AttributeType.NUMBER },
    sortKey: { name: 'id', type: AttributeType.NUMBER },
    projectionType: ProjectionType.ALL,
  })

  const guessSubmissions = simple('GuessSubmissionsTable', 'guess-submissions')
  guessSubmissions.addGlobalSecondaryIndex({
    indexName: 'byPlaylist',
    partitionKey: { name: 'playlistId', type: AttributeType.NUMBER },
    sortKey: { name: 'contributorId', type: AttributeType.NUMBER },
    projectionType: ProjectionType.ALL,
  })

  const songRatings = simple('SongRatingsTable', 'song-ratings')
  songRatings.addGlobalSecondaryIndex({
    indexName: 'byPlaylist',
    partitionKey: { name: 'playlistId', type: AttributeType.NUMBER },
    sortKey: { name: 'id', type: AttributeType.NUMBER },
    projectionType: ProjectionType.ALL,
  })

  const playlistRankings = simple('PlaylistRankingsTable', 'playlist-rankings')
  playlistRankings.addGlobalSecondaryIndex({
    indexName: 'byPlaylist',
    partitionKey: { name: 'playlistId', type: AttributeType.NUMBER },
    sortKey: { name: 'rankPosition', type: AttributeType.NUMBER },
    projectionType: ProjectionType.ALL,
  })

  return {
    playlists,
    songs,
    contributors,
    organizations,
    nominations,
    guesses,
    guessSubmissions,
    songRatings,
    playlistRankings,
  }
}

export type Tables = ReturnType<typeof createTables>
