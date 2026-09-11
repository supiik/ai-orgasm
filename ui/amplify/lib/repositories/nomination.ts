import { getItem, partitionKey, queryByPartitionKey, queryIndex, saveItem } from './base'

const TABLE = () => process.env.DYNAMODB_TABLE_NOMINATIONS ?? ''
const ENTITY_TYPE = 'NOMINATION'

export type NominationStatus = 'PENDING' | 'APPROVED' | 'DECLINED'

export interface NominationItem {
  pk: string
  sk: string
  id: bigint
  tenantId: number
  playlistId: bigint
  songId: bigint
  nominatedById: bigint
  status: NominationStatus
  createdAt: string
  updatedAt: string
  deletedAt?: string
  version?: number
}

export function nominationKey(tenantId: number, id: bigint) {
  return { pk: partitionKey(tenantId, ENTITY_TYPE), sk: id.toString() }
}

export async function findNominationById(tenantId: number, id: bigint): Promise<NominationItem | undefined> {
  const { pk, sk } = nominationKey(tenantId, id)
  return getItem<NominationItem>(TABLE(), pk, sk)
}

/** byPlaylist GSI: playlistId (HASH) + songId (RANGE) — also used for the uniqueness existence check. */
export function findNominationsByPlaylistId(playlistId: bigint): Promise<NominationItem[]> {
  return queryIndex<NominationItem>(TABLE(), 'byPlaylist', 'playlistId = :playlistId', { ':playlistId': playlistId })
}

export async function existsNominationForPlaylistAndSong(playlistId: bigint, songId: bigint): Promise<boolean> {
  const items = await queryIndex<NominationItem>(TABLE(), 'byPlaylist', 'playlistId = :playlistId AND songId = :songId', {
    ':playlistId': playlistId,
    ':songId': songId,
  })
  return items.length > 0
}

/** bySong GSI: songId (HASH) + id (RANGE) — cross-playlist lookup by song. */
export function findNominationsBySongId(songId: bigint): Promise<NominationItem[]> {
  return queryIndex<NominationItem>(TABLE(), 'bySong', 'songId = :songId', { ':songId': songId })
}

export function saveNomination(item: NominationItem): Promise<NominationItem> {
  return saveItem(TABLE(), item)
}

/** Query GSI1 for the playlist's nominations, filter PENDING, save each — DynamoDB has no bulk-update primitive. */
export async function declinePendingNominations(playlistId: bigint, now: string): Promise<number> {
  const pending = (await findNominationsByPlaylistId(playlistId)).filter((n) => n.status === 'PENDING')
  for (const item of pending) {
    item.status = 'DECLINED'
    item.updatedAt = now
    await saveItem(TABLE(), item)
  }
  return pending.length
}
