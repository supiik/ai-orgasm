import { ConflictError, NotFoundError, ValidationError } from '../errors'
import { formatId, generateId, parseId } from '../idGenerator'
import type { PageRequest } from '../http'
import { findAllPlaylistsByTenant, findPlaylistById, savePlaylist, type PlaylistItem, type PlaylistStatus } from '../repositories/playlist'
import { findContributorById, type ContributorItem } from '../repositories/contributor'
import { memoize } from '../memo'

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

// The two deadlines are the exception: they're normally stamped by open / start-guessing, but
// the lead may extend (or shorten) them afterwards — `updatePlaylist` enforces "lead only, and
// only while the playlist is in the phase that deadline governs".
export interface UpdatePlaylistRequest {
  name: string
  description?: string
  deadline?: string
  guessingDeadline?: string
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
  return buildResponse(item, (id) => findContributorById(tenantId, id))
}

/**
 * Batch form of `toPlaylistResponse`. Playlists in a page typically share a handful of leads, so
 * memoizing turns one GetItem per row into one per distinct lead.
 */
export function toPlaylistResponses(tenantId: number, items: PlaylistItem[]): Promise<PlaylistResponse[]> {
  const lookupLead = memoize((id: bigint) => findContributorById(tenantId, id))
  return Promise.all(items.map((item) => buildResponse(item, lookupLead)))
}

async function buildResponse(
  item: PlaylistItem,
  lookupLead: (id: bigint) => Promise<ContributorItem | undefined>,
): Promise<PlaylistResponse> {
  const lead = item.leadContributorId !== undefined ? await lookupLead(item.leadContributorId) : undefined
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
  const needle = filter.name?.trim().toLowerCase()
  const all = (await findAllPlaylistsByTenant(tenantId))
    .filter((item) => !item.deletedAt)
    .filter((item) => !needle || item.name?.toLowerCase().includes(needle))

  const paged = page ? all.slice(page.page * page.size, page.page * page.size + page.size) : all
  return { content: await toPlaylistResponses(tenantId, paged), totalElements: all.length }
}

export async function updatePlaylist(
  tenantId: number,
  id: string,
  actorId: bigint,
  request: UpdatePlaylistRequest,
): Promise<PlaylistResponse> {
  const existing = await requirePlaylistItem(tenantId, id)
  existing.name = request.name
  if (request.description !== undefined) existing.description = request.description
  if (request.deadline !== undefined) {
    assertDeadlineChangeAllowed(existing, actorId, 'OPEN', 'nomination deadline')
    existing.deadline = parseDeadline(request.deadline, 'deadline')
  }
  if (request.guessingDeadline !== undefined) {
    assertDeadlineChangeAllowed(existing, actorId, 'GUESSING', 'guessing deadline')
    existing.guessingDeadline = parseDeadline(request.guessingDeadline, 'guessingDeadline')
  }
  existing.updatedAt = new Date().toISOString()
  return toPlaylistResponse(tenantId, await savePlaylist(existing))
}

function assertDeadlineChangeAllowed(
  playlist: PlaylistItem,
  actorId: bigint,
  requiredStatus: PlaylistStatus,
  label: string,
): void {
  if (playlist.status !== requiredStatus) {
    throw new ConflictError(`Playlist must be ${requiredStatus} to change the ${label}`)
  }
  if (playlist.leadContributorId === undefined || playlist.leadContributorId !== actorId) {
    throw new ConflictError(`Only the lead contributor can change the ${label}`)
  }
}

function parseDeadline(value: string, field: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) throw new ValidationError([`${field} must be an ISO-8601 date-time`])
  return date.toISOString()
}

export async function deletePlaylist(tenantId: number, id: string): Promise<void> {
  const existing = await requirePlaylistItem(tenantId, id)
  existing.deletedAt = new Date().toISOString()
  await savePlaylist(existing)
}
