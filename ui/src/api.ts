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
    await keycloak.login()
  }
  return keycloak.token ?? ''
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

const songClient = {
  list:   (page?: number, size?: number, sort?: string, name?: string) => _songs.findAllSongs(page, size, sort, name),
  get:    (id: string) => _songs.findSongById(id),
  create: (body: CreateSongRequest) => _songs.createSong(body),
  update: (id: string, body: UpdateSongRequest) => _songs.updateSong(id, body),
  delete: (id: string) => _songs.deleteSong(id),
}

export type GuessEntry = { nominationId: string; guesserId: string; guessedContributorId: string }

const guessesClient = {
  list: async (playlistId: string): Promise<{ data: GuessEntry[] }> => {
    const token = await getAccessToken()
    const res = await fetch(`/api/v1/playlists/${playlistId}/guesses`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return { data: await res.json() }
  },
}

export const api = {
  contributors: () => contributorClient,
  playlists:    () => playlistClient,
  nominations:  () => nominationClient,
  songs:        () => songClient,
  guesses:      () => guessesClient,
}
