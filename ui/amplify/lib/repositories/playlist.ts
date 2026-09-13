import { getItem, partitionKey, queryByPartitionKey, saveItem } from './base'

const TABLE = () => process.env.DYNAMODB_TABLE_PLAYLISTS ?? ''
const ENTITY_TYPE = 'PLAYLIST'

export type PlaylistStatus = 'NEW' | 'OPEN' | 'GUESSING' | 'UNDER_EVALUATION' | 'CLOSED' | 'PUBLISHED'

export interface PlaylistItem {
  pk: string
  sk: string
  id: bigint
  tenantId: number
  name: string
  description?: string
  status: PlaylistStatus
  ratingType?: string
  leadContributorId?: bigint
  deadline?: string
  guessingDeadline?: string
  createdAt: string
  updatedAt: string
  deletedAt?: string
  version?: number
}

export function playlistKey(tenantId: number, id: bigint) {
  return { pk: partitionKey(tenantId, ENTITY_TYPE), sk: id.toString() }
}

export async function findPlaylistById(tenantId: number, id: bigint): Promise<PlaylistItem | undefined> {
  const { pk, sk } = playlistKey(tenantId, id)
  return getItem<PlaylistItem>(TABLE(), pk, sk)
}

export function findAllPlaylistsByTenant(tenantId: number): Promise<PlaylistItem[]> {
  return queryByPartitionKey<PlaylistItem>(TABLE(), partitionKey(tenantId, ENTITY_TYPE))
}

export function savePlaylist(item: PlaylistItem): Promise<PlaylistItem> {
  return saveItem(TABLE(), item)
}
