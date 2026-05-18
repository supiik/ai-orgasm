import {
  Configuration,
  ContributorsApi, PlaylistsApi, SongsApi,
  type CreateContributorRequest, type UpdateContributorRequest,
  type CreatePlaylistRequest, type UpdatePlaylistRequest,
  type CreateSongRequest, type UpdateSongRequest,
} from '@orgasm/backend-client'

const config = new Configuration({ basePath: '' })
const _contributors = new ContributorsApi(config)
const _playlists = new PlaylistsApi(config)
const _songs = new SongsApi(config)

const contributorClient = {
  list:   (page?: number, size?: number, sort?: string, name?: string) => _contributors.findAllContributors(page, size, sort, name),
  get:    (id: number) => _contributors.findContributorById(id),
  create: (body: CreateContributorRequest) => _contributors.createContributor(body),
  update: (id: number, body: UpdateContributorRequest) => _contributors.updateContributor(id, body),
  delete: (id: number) => _contributors.deleteContributor(id),
}

const playlistClient = {
  list:   (page?: number, size?: number, sort?: string, name?: string) => _playlists.findAllPlaylists(page, size, sort, name),
  get:    (id: number) => _playlists.findPlaylistById(id),
  create: (body: CreatePlaylistRequest) => _playlists.createPlaylist(body),
  update: (id: number, body: UpdatePlaylistRequest) => _playlists.updatePlaylist(id, body),
  delete: (id: number) => _playlists.deletePlaylist(id),
}

const songClient = {
  list:   (page?: number, size?: number, sort?: string, name?: string) => _songs.findAllSongs(page, size, sort, name),
  get:    (id: number) => _songs.findSongById(id),
  create: (body: CreateSongRequest) => _songs.createSong(body),
  update: (id: number, body: UpdateSongRequest) => _songs.updateSong(id, body),
  delete: (id: number) => _songs.deleteSong(id),
}

export const api = {
  contributors: () => contributorClient,
  playlists:    () => playlistClient,
  songs:        () => songClient,
}
