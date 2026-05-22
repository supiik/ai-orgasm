type PlaylistStatus = 'NEW' | 'OPEN' | 'GUESSING' | 'UNDER_EVALUATION' | 'CLOSED' | 'PUBLISHED'
type NominationStatus = 'PENDING' | 'APPROVED' | 'DECLINED'

export interface PlaylistRow {
  id: string
  name: string
  description: string | null
  status: PlaylistStatus
  leadContributorId: string | null
  leadContributorName: string | null
  leadContributorAvatarUrl: string | null
  deadline: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface NominationRow {
  id: string
  playlistId: string
  songId: string
  nominatedById: string
  status: NominationStatus
  version: number
  createdAt: string
  updatedAt: string
}

export const playlistsDb: PlaylistRow[] = [
  { id: 'play-a1b2c3d4e5f60718', name: 'Chill Vibes', description: 'Relaxing tunes', status: 'NEW', leadContributorId: null, leadContributorName: null, leadContributorAvatarUrl: null, deadline: null, version: 0, createdAt: '2024-01-01T10:00:00Z', updatedAt: '2024-01-01T10:00:00Z' },
  { id: 'play-2d3e4f5a6b7c8d90', name: 'Workout Hits', description: 'High energy bangers', status: 'OPEN', leadContributorId: 'cont-1a2b3c4d5e6f7089', leadContributorName: 'Thom Yorke', leadContributorAvatarUrl: 'https://i.pravatar.cc/150?u=thom', deadline: new Date(Date.now() + 86400000).toISOString(), version: 0, createdAt: '2024-01-02T12:00:00Z', updatedAt: '2024-01-02T12:00:00Z' },
  { id: 'play-e5f6a7b8c9d0e1f2', name: 'Late Night', description: null, status: 'OPEN', leadContributorId: 'cont-1a2b3c4d5e6f7089', leadContributorName: 'Thom Yorke', leadContributorAvatarUrl: 'https://i.pravatar.cc/150?u=thom', deadline: new Date(Date.now() - 3600000).toISOString(), version: 1, createdAt: '2024-01-03T23:00:00Z', updatedAt: '2024-01-10T01:00:00Z' },
  { id: 'play-b9c0d1e2f3a4b5c6', name: 'Road Trip Mix', description: 'Songs to guess!', status: 'GUESSING', leadContributorId: 'cont-1a2b3c4d5e6f7089', leadContributorName: 'Thom Yorke', leadContributorAvatarUrl: 'https://i.pravatar.cc/150?u=thom', deadline: new Date(Date.now() - 7200000).toISOString(), version: 2, createdAt: '2024-01-04T08:00:00Z', updatedAt: '2024-01-12T10:00:00Z' },
]

export const nominationsDb: NominationRow[] = [
  { id: 'nom-f1e2d3c4b5a69708', playlistId: 'play-2d3e4f5a6b7c8d90', songId: 'song-0af3b7c2d1e8f905', nominatedById: 'cont-1a2b3c4d5e6f7089', status: 'PENDING', version: 0, createdAt: '2024-01-10T10:00:00Z', updatedAt: '2024-01-10T10:00:00Z' },
  { id: 'nom-3c4d5e6f7a8b9c0d', playlistId: 'play-2d3e4f5a6b7c8d90', songId: 'song-9b2c5e3a7f1d4680', nominatedById: 'cont-0c1d2e3f4a5b6c7d', status: 'APPROVED', version: 1, createdAt: '2024-01-10T11:00:00Z', updatedAt: '2024-01-10T12:00:00Z' },
  { id: 'nom-7a8b9c0d1e2f3a4b', playlistId: 'play-e5f6a7b8c9d0e1f2', songId: 'song-c4d7a8e2f3b16509', nominatedById: 'cont-8f7e6d5c4b3a2019', status: 'PENDING', version: 0, createdAt: '2024-01-11T09:00:00Z', updatedAt: '2024-01-11T09:00:00Z' },
  { id: 'nom-a1b2c3d4e5f6a7b8', playlistId: 'play-b9c0d1e2f3a4b5c6', songId: 'song-0af3b7c2d1e8f905', nominatedById: 'cont-8f7e6d5c4b3a2019', status: 'APPROVED', version: 1, createdAt: '2024-01-12T08:00:00Z', updatedAt: '2024-01-12T09:00:00Z' },
  { id: 'nom-b2c3d4e5f6a7b8c9', playlistId: 'play-b9c0d1e2f3a4b5c6', songId: 'song-9b2c5e3a7f1d4680', nominatedById: 'cont-0c1d2e3f4a5b6c7d', status: 'APPROVED', version: 1, createdAt: '2024-01-12T08:30:00Z', updatedAt: '2024-01-12T09:00:00Z' },
  { id: 'nom-c3d4e5f6a7b8c9d0', playlistId: 'play-b9c0d1e2f3a4b5c6', songId: 'song-c4d7a8e2f3b16509', nominatedById: 'cont-1a2b3c4d5e6f7089', status: 'APPROVED', version: 1, createdAt: '2024-01-12T09:00:00Z', updatedAt: '2024-01-12T09:00:00Z' },
]
