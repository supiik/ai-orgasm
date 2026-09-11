import { fetchAuthSession } from 'aws-amplify/auth'
import outputs from '../amplify_outputs.json'
import type {
  CreateContributorRequest, UpdateContributorRequest, ContributorResponse, ContributorPage,
  CreatePlaylistRequest, UpdatePlaylistRequest, PlaylistResponse, PlaylistPage,
  CreateSongRequest, UpdateSongRequest, SongResponse, SongPage,
  OpenPlaylistRequest, PublishPlaylistRequest, StartGuessingRequest,
  NominateSongRequest, ReviewNominationRequest, SubmitGuessesRequest,
  NominationResponse, NominationPage,
} from '@orgasm/backend-client'
import type { GuessEntry, RankingEntry, SongRatingEntry, SongNomination } from './api-backend'

// Lambda-backed mirror of api-backend.ts's `api` object — same method names/shapes, so the view
// files that call `api.playlists().list(...)` etc. need no changes regardless of which one
// api.ts dispatches to. Each Function URL has no route templating (see CLAUDE.md "TypeScript
// Lambda API via Amplify Gen 2"), so {id}/action segments are appended to the base URL exactly
// as the corresponding ui/amplify/functions/*/handler.ts expects (verified against those
// handler bodies and the original REST paths the MSW mocks mirror, not guessed).
const functionUrls = (outputs as { custom?: { functionUrls?: Record<string, string> } }).custom?.functionUrls ?? {}

function functionUrl(name: string): string {
  const url = functionUrls[name]
  if (!url) throw new Error(`amplify_outputs.json custom.functionUrls["${name}"] is not configured`)
  return url.replace(/\/+$/, '')
}

async function authorizedFetch(input: string, init?: RequestInit): Promise<Response> {
  const session = await fetchAuthSession().catch(() => null)
  const token = session?.tokens?.idToken?.toString()
  const res = await fetch(input, {
    ...init,
    headers: {
      ...init?.headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })
  if (res.status === 401) {
    // Underlying views mount (and fire requests) even while CognitoLoginOverlay is showing on
    // top — a 401 before the user has ever signed in is expected, not an expired session. Only
    // flag sessionExpired when we previously believed we were authenticated.
    const { useAuthStore } = await import('@/stores/auth')
    const authStore = useAuthStore()
    if (authStore.isAuthenticated) authStore.sessionExpired = true
  }
  return res
}

async function req<T>(name: string, path: string, init?: RequestInit): Promise<{ data: T }> {
  const res = await authorizedFetch(`${functionUrl(name)}${path}`, {
    ...init,
    headers: { ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  if (res.status === 204) return { data: undefined as T }
  return { data: await res.json() }
}

function pageQuery(page?: number, size?: number, name?: string): string {
  const params = new URLSearchParams()
  if (page !== undefined) params.set('page', String(page))
  if (size !== undefined) params.set('size', String(size))
  if (name) params.set('name', name)
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

// Note: `sort` is accepted for signature parity with api-backend.ts but not applied — the
// Lambda list-* handlers don't support server-side sorting. Results come back in DynamoDB's
// natural order. See CLAUDE.md / the plan this shipped under for the known-gap note.

const contributorClient = {
  list:      (page?: number, size?: number, _sort?: string, name?: string) => req<ContributorPage>('list-contributors', pageQuery(page, size, name)),
  get:       (id: string) => req<ContributorResponse>('get-contributor', `/${id}`),
  me:        () => req<ContributorResponse>('get-current-contributor', ''),
  create:    (body: CreateContributorRequest) => req<ContributorResponse>('create-contributor', '', { method: 'POST', body: JSON.stringify(body) }),
  update:    (id: string, body: UpdateContributorRequest) => req<ContributorResponse>('update-contributor', `/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  delete:    (id: string) => req<void>('delete-contributor', `/${id}`, { method: 'DELETE' }),
  playlists: (id: string, page?: number, size?: number) => req<PlaylistPage>('list-playlists-by-contributor', `/${id}/playlists${pageQuery(page, size)}`),
}

const playlistClient = {
  list:          (page?: number, size?: number, _sort?: string, name?: string) => req<PlaylistPage>('list-playlists', pageQuery(page, size, name)),
  get:           (id: string) => req<PlaylistResponse>('get-playlist', `/${id}`),
  create:        (body: CreatePlaylistRequest) => req<PlaylistResponse>('create-playlist', '', { method: 'POST', body: JSON.stringify(body) }),
  update:        (id: string, body: UpdatePlaylistRequest) => req<PlaylistResponse>('update-playlist', `/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  delete:        (id: string) => req<void>('delete-playlist', `/${id}`, { method: 'DELETE' }),
  open:          (id: string, body: OpenPlaylistRequest) => req<PlaylistResponse>('open-playlist', `/${id}/open`, { method: 'POST', body: JSON.stringify(body) }),
  startGuessing: (id: string, body: StartGuessingRequest) => req<PlaylistResponse>('start-guessing', `/${id}/start-guessing`, { method: 'POST', body: JSON.stringify(body) }),
  submitGuesses: (id: string, body: SubmitGuessesRequest) => req<void>('submit-guesses', `/${id}/submit-guesses`, { method: 'POST', body: JSON.stringify(body) }),
  publish:       (id: string, body: PublishPlaylistRequest) => req<PlaylistResponse>('publish-playlist', `/${id}/publish`, { method: 'POST', body: JSON.stringify(body) }),
}

const nominationClient = {
  list:    (playlistId: string, page?: number, size?: number) => req<NominationPage>('list-nominations', `/${playlistId}/nominations${pageQuery(page, size)}`),
  create:  (playlistId: string, body: NominateSongRequest) => req<NominationResponse>('nominate-song', `/${playlistId}/nominations`, { method: 'POST', body: JSON.stringify(body) }),
  approve: (id: string, body: ReviewNominationRequest) => req<NominationResponse>('approve-nomination', `/${id}/approve`, { method: 'PUT', body: JSON.stringify(body) }),
  decline: (id: string, body: ReviewNominationRequest) => req<NominationResponse>('decline-nomination', `/${id}/decline`, { method: 'PUT', body: JSON.stringify(body) }),
}

const songClient = {
  list:   (page?: number, size?: number, _sort?: string, name?: string) => req<SongPage>('list-songs', pageQuery(page, size, name)),
  get:    (id: string) => req<SongResponse>('get-song', `/${id}`),
  create: (body: CreateSongRequest) => req<SongResponse>('create-song', '', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: string, body: UpdateSongRequest) => req<SongResponse>('update-song', `/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  delete: (id: string) => req<void>('delete-song', `/${id}`, { method: 'DELETE' }),
  nominations: async (id: string): Promise<SongNomination[]> => {
    const res = await authorizedFetch(`${functionUrl('list-song-nominations')}/${id}/nominations`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
  },
}

const guessesClient = {
  list: async (playlistId: string): Promise<{ data: GuessEntry[] }> => {
    const res = await authorizedFetch(`${functionUrl('list-guesses')}/${playlistId}/guesses`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return { data: await res.json() }
  },
}

const rankingsClient = {
  list: async (): Promise<{ data: RankingEntry[] }> => {
    const res = await authorizedFetch(functionUrl('list-rankings'))
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return { data: await res.json() }
  },
}

const songRatingsClient = {
  list: async (playlistId: string): Promise<{ data: SongRatingEntry[] }> => {
    const res = await authorizedFetch(`${functionUrl('list-song-ratings')}/${playlistId}/ratings`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return { data: await res.json() }
  },
  submit: async (playlistId: string, body: { contributorId: string; ratings: Array<{ nominationId: string; points: number }> }): Promise<void> => {
    const res = await authorizedFetch(`${functionUrl('submit-ratings')}/${playlistId}/ratings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
  },
}

export const api = {
  contributors: () => contributorClient,
  playlists:    () => playlistClient,
  nominations:  () => nominationClient,
  songs:        () => songClient,
  guesses:      () => guessesClient,
  rankings:     () => rankingsClient,
  songRatings:  () => songRatingsClient,
}
