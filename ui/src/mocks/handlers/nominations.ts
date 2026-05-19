import { http, HttpResponse } from 'msw'
import { db as playlistDb } from './playlists'

type NominationStatus = 'PENDING' | 'APPROVED' | 'DECLINED'

interface NominationResponse {
  id: string
  playlistId: string
  songId: string
  nominatedById: string
  status: NominationStatus
  version: number
  createdAt: string
  updatedAt: string
}

const nextId = () => `nom-${Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join('')}`

export const nominationsDb: NominationResponse[] = [
  { id: 'nom-f1e2d3c4b5a69708', playlistId: 'play-2d3e4f5a6b7c8d90', songId: 'song-0af3b7c2d1e8f905', nominatedById: 'cont-1a2b3c4d5e6f7089', status: 'PENDING', version: 0, createdAt: '2024-01-10T10:00:00Z', updatedAt: '2024-01-10T10:00:00Z' },
  { id: 'nom-3c4d5e6f7a8b9c0d', playlistId: 'play-2d3e4f5a6b7c8d90', songId: 'song-9b2c5e3a7f1d4680', nominatedById: 'cont-0c1d2e3f4a5b6c7d', status: 'APPROVED', version: 1, createdAt: '2024-01-10T11:00:00Z', updatedAt: '2024-01-10T12:00:00Z' },
  { id: 'nom-7a8b9c0d1e2f3a4b', playlistId: 'play-e5f6a7b8c9d0e1f2', songId: 'song-c4d7a8e2f3b16509', nominatedById: 'cont-8f7e6d5c4b3a2019', status: 'PENDING', version: 0, createdAt: '2024-01-11T09:00:00Z', updatedAt: '2024-01-11T09:00:00Z' },
]

const now = () => new Date().toISOString()

export const nominationHandlers = [
  http.get('/api/v1/playlists/:id/nominations', ({ params, request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const filtered = nominationsDb.filter(n => n.playlistId === params.id)
    const content = filtered.slice(page * size, page * size + size)
    return HttpResponse.json({
      content,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      number: page,
      size,
    })
  }),

  http.post('/api/v1/playlists/:id/nominations', async ({ params, request }) => {
    const playlist = playlistDb.find(p => p.id === params.id)
    if (!playlist) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    if (playlist.status !== 'OPEN') {
      return HttpResponse.json({ title: 'Conflict', detail: 'Playlist is not open for nominations' }, { status: 409 })
    }
    if (playlist.deadline && new Date(playlist.deadline) < new Date()) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Nomination deadline has passed' }, { status: 409 })
    }
    const body = await request.json() as { contributorId: string; songId: string }
    const alreadyNominated = nominationsDb.some(n => n.playlistId === params.id && n.songId === body.songId)
    if (alreadyNominated) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Song already nominated' }, { status: 409 })
    }
    const created: NominationResponse = {
      id: nextId(),
      playlistId: params.id as string,
      songId: body.songId,
      nominatedById: body.contributorId,
      status: 'PENDING',
      version: 0,
      createdAt: now(),
      updatedAt: now(),
    }
    nominationsDb.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put('/api/v1/nominations/:id/approve', async ({ params, request }) => {
    const index = nominationsDb.findIndex(n => n.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    const body = await request.json() as { reviewerId: string }
    const playlist = playlistDb.find(p => p.id === nominationsDb[index].playlistId)
    if (playlist?.leadContributorId !== body.reviewerId) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only the lead contributor can review nominations' }, { status: 409 })
    }
    nominationsDb[index] = { ...nominationsDb[index], status: 'APPROVED', version: nominationsDb[index].version + 1, updatedAt: now() }
    return HttpResponse.json(nominationsDb[index])
  }),

  http.put('/api/v1/nominations/:id/decline', async ({ params, request }) => {
    const index = nominationsDb.findIndex(n => n.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    const body = await request.json() as { reviewerId: string }
    const playlist = playlistDb.find(p => p.id === nominationsDb[index].playlistId)
    if (playlist?.leadContributorId !== body.reviewerId) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only the lead contributor can review nominations' }, { status: 409 })
    }
    nominationsDb[index] = { ...nominationsDb[index], status: 'DECLINED', version: nominationsDb[index].version + 1, updatedAt: now() }
    return HttpResponse.json(nominationsDb[index])
  }),
]
