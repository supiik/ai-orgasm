import { NotFoundError } from '../errors'
import { formatId, generateId, parseId } from '../idGenerator'
import type { PageRequest } from '../http'
import { findAllPlaylistsByTenant, findPlaylistById, savePlaylist, type PlaylistItem, type PlaylistStatus } from '../repositories/playlist'
import { findContributorById } from '../repositories/contributor'

export type RatingType = 'LINEAR' | 'FIBONACCI' | 'BEST_SONG'

// `status` is deliberately absent from both request types: it may only advance through the
// workflow endpoints (open / start-guessing / publish), which enforce the legal transitions and
// the lead-contributor checks. Accepting it here let any caller set PUBLISHED directly and skip
// both — including the ranking computation that publishing is supposed to trigger.
export interface CreatePlaylistRequest {
  name: string
  description?: string
  ratingType?: RatingType
}

export interface UpdatePlaylistRequest {
  name: string
  description?: string
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

// leadContributorName/leadContributorAvatarUrl are hydrated via a lookup-at-read GetItem,
// matching backend-core's PlaylistMapper (which always resolves the JPA-lazy leadContributor
// relation) — every read path returns the name, not just the Orgasm workflow methods.
export async function toPlaylistResponse(tenantId: number, item: PlaylistItem): Promise<PlaylistResponse> {
  const lead = item.leadContributorId !== undefined ? await findContributorById(tenantId, item.leadContributorId) : undefined
  return {
    id: formatId('play', item.id),
    name: item.name,
    description: item.description,
    status: item.status,
    ratingType: item.ratingType as RatingType | undefined,
    leadContributorId: item.leadContributorId ? formatId('cont', item.leadContributorId) : undefined,
    leadContributorName: lead?.name,
    leadContributorAvatarUrl: lead?.avatarUrl,
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
    status: 'NEW',
    ratingType: request.ratingType,
    createdAt: now,
    updatedAt: now,
  }
  return toPlaylistResponse(tenantId, await savePlaylist(item))
}

export async function getPlaylistById(tenantId: number, id: string): Promise<PlaylistResponse | undefined> {
  const item = await findPlaylistById(tenantId, parseId(id))
  return item && !item.deletedAt ? toPlaylistResponse(tenantId, item) : undefined
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
  return { content: await Promise.all(paged.map((item) => toPlaylistResponse(tenantId, item))), totalElements: all.length }
}

export async function updatePlaylist(tenantId: number, id: string, request: UpdatePlaylistRequest): Promise<PlaylistResponse> {
  const existing = await requirePlaylistItem(tenantId, id)
  existing.name = request.name
  if (request.description !== undefined) existing.description = request.description
  existing.updatedAt = new Date().toISOString()
  return toPlaylistResponse(tenantId, await savePlaylist(existing))
}

export async function deletePlaylist(tenantId: number, id: string): Promise<void> {
  const existing = await requirePlaylistItem(tenantId, id)
  existing.deletedAt = new Date().toISOString()
  await savePlaylist(existing)
}
