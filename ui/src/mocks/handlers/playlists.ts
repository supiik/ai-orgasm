import { http, HttpResponse } from 'msw'

type PlaylistStatus = 'NEW' | 'OPEN' | 'UNDER_EVALUATION' | 'CLOSED' | 'PUBLISHED'

interface PlaylistResponse {
  id: string
  name: string
  description: string | null
  status: PlaylistStatus
  leadContributorId: string | null
  deadline: string | null
  version: number
  createdAt: string
  updatedAt: string
}

const nextId = () => `play-${Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join('')}`

export const db: PlaylistResponse[] = [
  { id: 'play-a1b2c3d4e5f60718', name: 'Chill Vibes', description: 'Relaxing tunes', status: 'NEW', leadContributorId: null, deadline: null, version: 0, createdAt: '2024-01-01T10:00:00Z', updatedAt: '2024-01-01T10:00:00Z' },
  { id: 'play-2d3e4f5a6b7c8d90', name: 'Workout Hits', description: 'High energy bangers', status: 'OPEN', leadContributorId: 'cont-1a2b3c4d5e6f7089', deadline: new Date(Date.now() + 86400000).toISOString(), version: 0, createdAt: '2024-01-02T12:00:00Z', updatedAt: '2024-01-02T12:00:00Z' },
  { id: 'play-e5f6a7b8c9d0e1f2', name: 'Late Night', description: null, status: 'OPEN', leadContributorId: 'cont-1a2b3c4d5e6f7089', deadline: new Date(Date.now() - 3600000).toISOString(), version: 1, createdAt: '2024-01-03T23:00:00Z', updatedAt: '2024-01-10T01:00:00Z' },
]

const now = () => new Date().toISOString()

export const playlistHandlers = [
  http.get('/api/v1/playlists', ({ request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const name = url.searchParams.get('name')?.toLowerCase()
    const filtered = name ? db.filter(p => p.name.toLowerCase().includes(name)) : db
    const content = filtered.slice(page * size, page * size + size)
    return HttpResponse.json({
      content,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      number: page,
      size,
    })
  }),

  http.post('/api/v1/playlists', async ({ request }) => {
    const body = await request.json() as { name: string; description?: string }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    const created: PlaylistResponse = {
      id: nextId(),
      name: body.name,
      description: body.description ?? null,
      status: 'NEW',
      leadContributorId: null,
      deadline: null,
      version: 0,
      createdAt: now(),
      updatedAt: now(),
    }
    db.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.get('/api/v1/playlists/:id', ({ params }) => {
    const playlist = db.find(p => p.id === params.id)
    if (!playlist) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    return HttpResponse.json(playlist)
  }),

  http.put('/api/v1/playlists/:id', async ({ params, request }) => {
    const index = db.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    const body = await request.json() as { name: string; description?: string }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    db[index] = {
      ...db[index],
      name: body.name,
      description: body.description ?? null,
      version: db[index].version + 1,
      updatedAt: now(),
    }
    return HttpResponse.json(db[index])
  }),

  http.delete('/api/v1/playlists/:id', ({ params }) => {
    const index = db.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    db.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/api/v1/playlists/:id/open', async ({ params, request }) => {
    const index = db.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    if (db[index].status !== 'NEW') {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only NEW playlists can be opened' }, { status: 409 })
    }
    const body = await request.json() as { contributorId: string; deadline: string }
    db[index] = { ...db[index], status: 'OPEN', leadContributorId: body.contributorId, deadline: body.deadline, updatedAt: now() }
    return HttpResponse.json(db[index])
  }),

  http.post('/api/v1/playlists/:id/publish', async ({ params, request }) => {
    const index = db.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    if (db[index].status !== 'OPEN') {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only open playlists can be published' }, { status: 409 })
    }
    const body = await request.json() as { contributorId: string }
    if (db[index].leadContributorId !== body.contributorId) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only the lead contributor can publish' }, { status: 409 })
    }
    if (db[index].deadline && new Date(db[index].deadline!) > new Date()) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Deadline has not yet passed' }, { status: 409 })
    }
    db[index] = { ...db[index], status: 'PUBLISHED', updatedAt: now() }
    return HttpResponse.json(db[index])
  }),

  http.get('/api/v1/contributors/:id/playlists', ({ params, request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const filtered = db.filter(p => p.leadContributorId === params.id)
    const content = filtered.slice(page * size, page * size + size)
    return HttpResponse.json({
      content,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      number: page,
      size,
    })
  }),
]
