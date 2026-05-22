import { http, HttpResponse } from 'msw'
import { playlistsDb, nominationsDb } from './db'

export { nominationsDb }

const nextId = () => `nom-${Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join('')}`

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
    const playlist = playlistsDb.find(p => p.id === params.id)
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
    const created = {
      id: nextId(),
      playlistId: params.id as string,
      songId: body.songId,
      nominatedById: body.contributorId,
      status: 'PENDING' as const,
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
    const playlist = playlistsDb.find(p => p.id === nominationsDb[index].playlistId)
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
    const playlist = playlistsDb.find(p => p.id === nominationsDb[index].playlistId)
    if (playlist?.leadContributorId !== body.reviewerId) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only the lead contributor can review nominations' }, { status: 409 })
    }
    nominationsDb[index] = { ...nominationsDb[index], status: 'DECLINED', version: nominationsDb[index].version + 1, updatedAt: now() }
    return HttpResponse.json(nominationsDb[index])
  }),
]
