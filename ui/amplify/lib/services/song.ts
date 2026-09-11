import { NotFoundError } from '../errors'
import { formatId, generateId, parseId } from '../idGenerator'
import type { PageRequest } from '../http'
import { findAllSongsByTenant, findSongById, saveSong, type SongItem } from '../repositories/song'

export interface CreateSongRequest {
  artist: string
  name: string
  album?: string
  releaseYear?: number
  url?: string
}

export type UpdateSongRequest = CreateSongRequest

export interface SongResponse {
  id: string
  artist: string
  name: string
  album?: string
  releaseYear?: number
  url?: string
  version?: number
  createdAt: string
  updatedAt: string
}

export function toSongResponse(item: SongItem): SongResponse {
  return {
    id: formatId('song', item.id),
    artist: item.artist,
    name: item.name,
    album: item.album,
    releaseYear: item.releaseYear,
    url: item.url,
    version: item.version,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  }
}

export async function createSong(tenantId: number, request: CreateSongRequest): Promise<SongResponse> {
  const id = generateId()
  const now = new Date().toISOString()
  const item: SongItem = {
    pk: `${tenantId}#SONG`,
    sk: id.toString(),
    id,
    tenantId,
    artist: request.artist,
    name: request.name,
    album: request.album,
    releaseYear: request.releaseYear,
    url: request.url,
    createdAt: now,
    updatedAt: now,
  }
  return toSongResponse(await saveSong(item))
}

export async function getSongById(tenantId: number, id: string): Promise<SongResponse | undefined> {
  const item = await findSongById(tenantId, parseId(id))
  return item && !item.deletedAt ? toSongResponse(item) : undefined
}

export async function requireSongItem(tenantId: number, id: string): Promise<SongItem> {
  const item = await findSongById(tenantId, parseId(id))
  if (!item || item.deletedAt) throw new NotFoundError(`Song not found: ${id}`)
  return item
}

export async function listSongs(
  tenantId: number,
  filter: { name?: string },
  page: PageRequest | undefined,
): Promise<{ content: SongResponse[]; totalElements: number }> {
  const all = (await findAllSongsByTenant(tenantId))
    .filter((item) => !item.deletedAt)
    .filter((item) => !filter.name || item.name?.toLowerCase().includes(filter.name.toLowerCase()))

  const paged = page ? all.slice(page.page * page.size, page.page * page.size + page.size) : all
  return { content: paged.map(toSongResponse), totalElements: all.length }
}

export async function updateSong(tenantId: number, id: string, request: UpdateSongRequest): Promise<SongResponse> {
  const existing = await requireSongItem(tenantId, id)
  existing.artist = request.artist
  existing.name = request.name
  if (request.album !== undefined) existing.album = request.album
  if (request.releaseYear !== undefined) existing.releaseYear = request.releaseYear
  if (request.url !== undefined) existing.url = request.url
  existing.updatedAt = new Date().toISOString()
  return toSongResponse(await saveSong(existing))
}

export async function deleteSong(tenantId: number, id: string): Promise<void> {
  const existing = await requireSongItem(tenantId, id)
  existing.deletedAt = new Date().toISOString()
  await saveSong(existing)
}
