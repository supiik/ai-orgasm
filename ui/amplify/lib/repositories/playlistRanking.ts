import { partitionKey, queryByPartitionKey, queryIndex, saveItem } from './base'

const TABLE = () => process.env.DYNAMODB_TABLE_PLAYLIST_RANKINGS ?? ''
const ENTITY_TYPE = 'PLAYLIST_RANKING'

export interface PlaylistRankingItem {
  pk: string
  sk: string
  id: bigint
  tenantId: number
  playlistId: bigint
  contributorId: bigint
  rankPosition: number
  correctGuesses: number
  totalGuesses: number
  createdAt: string
  updatedAt: string
  deletedAt?: string
  version?: number
}

export function playlistRankingKey(tenantId: number, id: bigint) {
  return { pk: partitionKey(tenantId, ENTITY_TYPE), sk: id.toString() }
}

/** byPlaylist GSI: playlistId (HASH) + rankPosition (RANGE, numeric) — naturally rank-ordered. */
export function findRankingsByPlaylistId(playlistId: bigint): Promise<PlaylistRankingItem[]> {
  return queryIndex<PlaylistRankingItem>(TABLE(), 'byPlaylist', 'playlistId = :playlistId', { ':playlistId': playlistId })
}

/** The tenant's own base-table partition — used for the global GET /rankings across all playlists. */
export function findAllRankingsByTenant(tenantId: number): Promise<PlaylistRankingItem[]> {
  return queryByPartitionKey<PlaylistRankingItem>(TABLE(), partitionKey(tenantId, ENTITY_TYPE))
}

export function saveRanking(item: PlaylistRankingItem): Promise<PlaylistRankingItem> {
  return saveItem(TABLE(), item)
}
