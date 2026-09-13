import {
  Configuration,
  ContributorsApi, PlaylistsApi, SongsApi, NominationsApi,
  type CreateContributorRequest, type UpdateContributorRequest,
  type CreatePlaylistRequest, type UpdatePlaylistRequest,
  type CreateSongRequest, type UpdateSongRequest,
  type OpenPlaylistRequest, type PublishPlaylistRequest, type StartGuessingRequest,
  type NominateSongRequest, type ReviewNominationRequest, type SubmitGuessesRequest, type GuessItem,
} from '@orgasm/backend-client'

async function getAccessToken(): Promise<string> {
  if (import.meta.env.VITE_MOCK === 'true') return ''
  const keycloak = (await import('./keycloak')).default
  if (!keycloak.authenticated) return ''
  try {
    await keycloak.updateToken(60)
  } catch {
    const { useAuthStore } = await import('@/stores/auth')
    useAuthStore().sessionExpired = true
    throw new Error('Session expired')
  }
  return keycloak.token ?? ''
}

async function authenticatedFetch(input: string, init?: RequestInit): Promise<Response> {
  const token = await getAccessToken()
  const res = await fetch(input, {
    ...init,
    headers: {
      ...init?.headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })
  if (res.status === 401) {
    const { useAuthStore } = await import('@/stores/auth')
    useAuthStore().sessionExpired = true
  }
  return res
}

const config = new Configuration({
  basePath: '',
  accessToken: getAccessToken,
})
const _contributors = new ContributorsApi(config)
const _playlists = new PlaylistsApi(config)
const _songs = new SongsApi(config)
const _nominations = new NominationsApi(config)

const contributorClient = {
  list:          (page?: number, size?: number, sort?: string, name?: string) => _contributors.findAllContributors(page, size, sort, name),
  get:           (id: string) => _contributors.findContributorById(id),
  me:            () => _contributors.findCurrentContributor(),
  create:        (body: CreateContributorRequest) => _contributors.createContributor(body),
  update:        (id: string, body: UpdateContributorRequest) => _contributors.updateContributor(id, body),
  delete:        (id: string) => _contributors.deleteContributor(id),
  playlists:     (id: string, page?: number, size?: number) => _contributors.findPlaylistsByContributor(id, page, size),
}

const playlistClient = {
  list:    (page?: number, size?: number, sort?: string, name?: string) => _playlists.findAllPlaylists(page, size, sort, name),
  get:     (id: string) => _playlists.findPlaylistById(id),
  create:  (body: CreatePlaylistRequest) => _playlists.createPlaylist(body),
  update:  (id: string, body: UpdatePlaylistRequest) => _playlists.updatePlaylist(id, body),
  delete:  (id: string) => _playlists.deletePlaylist(id),
  open:          (id: string, body: OpenPlaylistRequest) => _playlists.openPlaylist(id, body),
  startGuessing: (id: string, body: StartGuessingRequest) => _playlists.startGuessing(id, body),
  submitGuesses: (id: string, body: SubmitGuessesRequest) => _playlists.submitGuesses(id, body),
  publish:       (id: string, body: PublishPlaylistRequest) => _playlists.publishPlaylist(id, body),
}

const nominationClient = {
  list:    (playlistId: string, page?: number, size?: number) => _nominations.findNominations(playlistId, page, size),
  create:  (playlistId: string, body: NominateSongRequest) => _nominations.nominateSong(playlistId, body),
  approve: (id: string, body: ReviewNominationRequest) => _nominations.approveNomination(id, body),
  decline: (id: string, body: ReviewNominationRequest) => _nominations.declineNomination(id, body),
}

export type SongNomination = {
  id: string
  playlistId: string
  playlistName: string
  nominatedById: string
  nominatedByName: string
  status: string
}

const songClient = {
  list:   (page?: number, size?: number, sort?: string, name?: string) => _songs.findAllSongs(page, size, sort, name),
  get:    (id: string) => _songs.findSongById(id),
  create: (body: CreateSongRequest) => _songs.createSong(body),
  update: (id: string, body: UpdateSongRequest) => _songs.updateSong(id, body),
  delete: (id: string) => _songs.deleteSong(id),
  nominations: async (id: string): Promise<SongNomination[]> => {
    const res = await authenticatedFetch(`/api/v1/songs/${id}/nominations`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
  },
}

export type GuessEntry = { nominationId: string; guesserId: string; guessedContributorId: string }

export type RankingEntry = {
  playlistId: string
  playlistName: string
  contributorId: string
  contributorName: string
  contributorAvatarUrl: string | null
  rankPosition: number
  correctGuesses: number
  totalGuesses: number
}

const guessesClient = {
  list: async (playlistId: string): Promise<{ data: GuessEntry[] }> => {
    const res = await authenticatedFetch(`/api/v1/playlists/${playlistId}/guesses`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return { data: await res.json() }
  },
}

const rankingsClient = {
  list: async (): Promise<{ data: RankingEntry[] }> => {
    const res = await authenticatedFetch('/api/v1/rankings')
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return { data: await res.json() }
  },
}

export type SongRatingEntry = {
  nominationId: string
  contributorId: string
  contributorName: string
  points: number
}

const songRatingsClient = {
  list: async (playlistId: string): Promise<{ data: SongRatingEntry[] }> => {
    const res = await authenticatedFetch(`/api/v1/playlists/${playlistId}/ratings`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return { data: await res.json() }
  },
  submit: async (playlistId: string, body: { contributorId: string; ratings: Array<{ nominationId: string; points: number }> }): Promise<void> => {
    const res = await authenticatedFetch(`/api/v1/playlists/${playlistId}/ratings`, {
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
