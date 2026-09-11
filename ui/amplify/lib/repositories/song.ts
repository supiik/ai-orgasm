import { getItem, partitionKey, queryByPartitionKey, saveItem } from './base'

const TABLE = () => process.env.DYNAMODB_TABLE_SONGS ?? ''
const ENTITY_TYPE = 'SONG'

export interface SongItem {
  pk: string
  sk: string
  id: bigint
  tenantId: number
  artist: string
  name: string
  album?: string
  releaseYear?: number
  url?: string
  createdAt: string
  updatedAt: string
  deletedAt?: string
  version?: number
}

export function songKey(tenantId: number, id: bigint) {
  return { pk: partitionKey(tenantId, ENTITY_TYPE), sk: id.toString() }
}

export async function findSongById(tenantId: number, id: bigint): Promise<SongItem | undefined> {
  const { pk, sk } = songKey(tenantId, id)
  return getItem<SongItem>(TABLE(), pk, sk)
}

export function findAllSongsByTenant(tenantId: number): Promise<SongItem[]> {
  return queryByPartitionKey<SongItem>(TABLE(), partitionKey(tenantId, ENTITY_TYPE))
}

export function saveSong(item: SongItem): Promise<SongItem> {
  return saveItem(TABLE(), item)
}
