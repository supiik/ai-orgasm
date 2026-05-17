import { http, HttpResponse } from 'msw'

type PlaylistStatus = 'NEW' | 'OPEN' | 'UNDER_EVALUATION' | 'CLOSED'

interface PlaylistResponse {
  id: number
  name: string
  description: string | null
  status: PlaylistStatus
  version: number
  createdAt: string
  updatedAt: string
}

let nextId = 4
const db: PlaylistResponse[] = [
  { id: 1, name: 'Chill Vibes', description: 'Relaxing tunes', status: 'NEW', version: 0, createdAt: '2024-01-01T10:00:00Z', updatedAt: '2024-01-01T10:00:00Z' },
  { id: 2, name: 'Workout Hits', description: 'High energy bangers', status: 'OPEN', version: 0, createdAt: '2024-01-02T12:00:00Z', updatedAt: '2024-01-02T12:00:00Z' },
  { id: 3, name: 'Late Night', description: null, status: 'UNDER_EVALUATION', version: 1, createdAt: '2024-01-03T23:00:00Z', updatedAt: '2024-01-10T01:00:00Z' },
]

const now = () => new Date().toISOString()

export const playlistHandlers = [
  http.get('/api/v1/playlists', ({ request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const content = db.slice(page * size, page * size + size)
    return HttpResponse.json({
      content,
      totalElements: db.length,
      totalPages: Math.ceil(db.length / size),
      number: page,
      size,
    })
  }),

  http.post('/api/v1/playlists', async ({ request }) => {
    const body = await request.json() as { name: string; description?: string; status?: PlaylistStatus }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    const created: PlaylistResponse = {
      id: nextId++,
      name: body.name,
      description: body.description ?? null,
      status: body.status ?? 'NEW',
      version: 0,
      createdAt: now(),
      updatedAt: now(),
    }
    db.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.get('/api/v1/playlists/:id', ({ params }) => {
    const playlist = db.find(p => p.id === Number(params.id))
    if (!playlist) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    return HttpResponse.json(playlist)
  }),

  http.put('/api/v1/playlists/:id', async ({ params, request }) => {
    const index = db.findIndex(p => p.id === Number(params.id))
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    const body = await request.json() as { name: string; description?: string; status?: PlaylistStatus }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    db[index] = {
      ...db[index],
      name: body.name,
      description: body.description ?? null,
      status: body.status ?? db[index].status,
      version: db[index].version + 1,
      updatedAt: now(),
    }
    return HttpResponse.json(db[index])
  }),

  http.delete('/api/v1/playlists/:id', ({ params }) => {
    const index = db.findIndex(p => p.id === Number(params.id))
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    db.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),
]
