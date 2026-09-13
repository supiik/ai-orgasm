import { DeleteCommand } from '@aws-sdk/lib-dynamodb'
import { ddb } from '../dynamodb'
import { partitionKey, queryByPartitionKey, queryIndex, saveItem } from './base'

const TABLE = () => process.env.DYNAMODB_TABLE_SONG_RATINGS ?? ''
const ENTITY_TYPE = 'SONG_RATING'

export interface SongRatingItem {
  pk: string
  sk: string
  id: bigint
  tenantId: number
  playlistId: bigint
  contributorId: bigint
  nominationId: bigint
  points: number
  createdAt: string
  updatedAt: string
  deletedAt?: string
  version?: number
}

export function songRatingKey(tenantId: number, id: bigint) {
  return { pk: partitionKey(tenantId, ENTITY_TYPE), sk: id.toString() }
}

/** byPlaylist GSI: playlistId (HASH) + id (RANGE). */
export function findSongRatingsByPlaylistId(playlistId: bigint): Promise<SongRatingItem[]> {
  return queryIndex<SongRatingItem>(TABLE(), 'byPlaylist', 'playlistId = :playlistId', { ':playlistId': playlistId })
}

/** Whole tenant partition (includes soft-deleted rows) — only the admin export reads at this scope. */
export function findAllSongRatingsByTenant(tenantId: number): Promise<SongRatingItem[]> {
  return queryByPartitionKey<SongRatingItem>(TABLE(), partitionKey(tenantId, ENTITY_TYPE))
}

export function saveSongRating(item: SongRatingItem): Promise<SongRatingItem> {
  return saveItem(TABLE(), item)
}

export async function deleteSongRating(tenantId: number, id: bigint): Promise<void> {
  const { pk, sk } = songRatingKey(tenantId, id)
  await ddb.send(new DeleteCommand({ TableName: TABLE(), Key: { pk, sk } }))
}

export async function deleteSongRatingsByPlaylistAndContributor(playlistId: bigint, contributorId: bigint): Promise<void> {
  const items = (await findSongRatingsByPlaylistId(playlistId)).filter((r) => r.contributorId === contributorId)
  for (const item of items) {
    await deleteSongRating(item.tenantId, item.id)
  }
}
