import { NotFoundError } from '../errors'
import { formatId, generateId, parseId } from '../idGenerator'
import type { PageRequest } from '../http'
import { findAllPlaylistsByTenant, findPlaylistById, savePlaylist, type PlaylistItem, type PlaylistStatus } from '../repositories/playlist'

export type RatingType = 'LINEAR' | 'FIBONACCI' | 'BEST_SONG'

export interface CreatePlaylistRequest {
  name: string
  description?: string
  status?: PlaylistStatus
  ratingType?: RatingType
}

export interface UpdatePlaylistRequest {
  name: string
  description?: string
  status?: PlaylistStatus
}

export interface PlaylistResponse {
  id: string
  name: string
  description?: string
  status: PlaylistStatus
  ratingType?: RatingType
  leadContributorId?: string
  leadContributorName?: string
  leadContributorAvatarUrl?: string
  deadline?: string
  guessingDeadline?: string
  version?: number
  createdAt: string
  updatedAt: string
}

// leadContributorName/leadContributorAvatarUrl are deliberately left unset here — that
// lookup-at-read hydration only happens in the Orgasm workflow (openPlaylist,
// findPlaylistsByContributor), mirroring PlaylistMapper.toResponse/OrgasmService.java exactly.
export function toPlaylistResponse(item: PlaylistItem): PlaylistResponse {
  return {
    id: formatId('play', item.id),
    name: item.name,
    description: item.description,
    status: item.status,
    ratingType: item.ratingType as RatingType | undefined,
    leadContributorId: item.leadContributorId ? formatId('cont', item.leadContributorId) : undefined,
    deadline: item.deadline,
    guessingDeadline: item.guessingDeadline,
    version: item.version,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  }
}

export async function createPlaylist(tenantId: number, request: CreatePlaylistRequest): Promise<PlaylistResponse> {
  const id = generateId()
  const now = new Date().toISOString()
  const item: PlaylistItem = {
    pk: `${tenantId}#PLAYLIST`,
    sk: id.toString(),
    id,
    tenantId,
    name: request.name,
    description: request.description,
    status: request.status ?? 'NEW',
    ratingType: request.ratingType,
    createdAt: now,
    updatedAt: now,
  }
  return toPlaylistResponse(await savePlaylist(item))
}

export async function getPlaylistById(tenantId: number, id: string): Promise<PlaylistResponse | undefined> {
  const item = await findPlaylistById(tenantId, parseId(id))
  return item && !item.deletedAt ? toPlaylistResponse(item) : undefined
}

export async function requirePlaylistItem(tenantId: number, id: string): Promise<PlaylistItem> {
  const item = await findPlaylistById(tenantId, parseId(id))
  if (!item || item.deletedAt) throw new NotFoundError(`Playlist not found: ${id}`)
  return item
}

export async function listPlaylists(
  tenantId: number,
  filter: { name?: string },
  page: PageRequest | undefined,
): Promise<{ content: PlaylistResponse[]; totalElements: number }> {
  const all = (await findAllPlaylistsByTenant(tenantId))
    .filter((item) => !item.deletedAt)
    .filter((item) => !filter.name || item.name?.toLowerCase().includes(filter.name.toLowerCase()))

  const paged = page ? all.slice(page.page * page.size, page.page * page.size + page.size) : all
  return { content: paged.map(toPlaylistResponse), totalElements: all.length }
}

export async function updatePlaylist(tenantId: number, id: string, request: UpdatePlaylistRequest): Promise<PlaylistResponse> {
  const existing = await requirePlaylistItem(tenantId, id)
  existing.name = request.name
  if (request.description !== undefined) existing.description = request.description
  if (request.status !== undefined) existing.status = request.status
  existing.updatedAt = new Date().toISOString()
  return toPlaylistResponse(await savePlaylist(existing))
}

export async function deletePlaylist(tenantId: number, id: string): Promise<void> {
  const existing = await requirePlaylistItem(tenantId, id)
  existing.deletedAt = new Date().toISOString()
  await savePlaylist(existing)
}
