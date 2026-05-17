import { http, HttpResponse } from 'msw'

interface SongResponse {
  id: number
  artist: string
  name: string
  album: string | null
  releaseYear: number | null
  version: number
  createdAt: string
  updatedAt: string
}

let nextId = 4
const db: SongResponse[] = [
  { id: 1, artist: 'Radiohead', name: 'Creep', album: 'Pablo Honey', releaseYear: 1993, version: 0, createdAt: '2024-01-01T10:00:00Z', updatedAt: '2024-01-01T10:00:00Z' },
  { id: 2, artist: 'Nirvana', name: 'Smells Like Teen Spirit', album: 'Nevermind', releaseYear: 1991, version: 0, createdAt: '2024-01-02T12:00:00Z', updatedAt: '2024-01-02T12:00:00Z' },
  { id: 3, artist: 'Oasis', name: 'Wonderwall', album: null, releaseYear: null, version: 1, createdAt: '2024-01-03T23:00:00Z', updatedAt: '2024-01-10T01:00:00Z' },
]

const now = () => new Date().toISOString()

export const songHandlers = [
  http.get('/v1/songs', ({ request }) => {
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

  http.post('/v1/songs', async ({ request }) => {
    const body = await request.json() as { artist: string; name: string; album?: string; releaseYear?: number }
    if (!body.artist?.trim()) {
      return HttpResponse.json({ message: 'Artist is required' }, { status: 400 })
    }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    const created: SongResponse = {
      id: nextId++,
      artist: body.artist,
      name: body.name,
      album: body.album ?? null,
      releaseYear: body.releaseYear ?? null,
      version: 0,
      createdAt: now(),
      updatedAt: now(),
    }
    db.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.get('/v1/songs/:id', ({ params }) => {
    const song = db.find(s => s.id === Number(params.id))
    if (!song) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    return HttpResponse.json(song)
  }),

  http.put('/v1/songs/:id', async ({ params, request }) => {
    const index = db.findIndex(s => s.id === Number(params.id))
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    const body = await request.json() as { artist: string; name: string; album?: string; releaseYear?: number }
    if (!body.artist?.trim()) {
      return HttpResponse.json({ message: 'Artist is required' }, { status: 400 })
    }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    db[index] = {
      ...db[index],
      artist: body.artist,
      name: body.name,
      album: body.album ?? null,
      releaseYear: body.releaseYear ?? null,
      version: db[index].version + 1,
      updatedAt: now(),
    }
    return HttpResponse.json(db[index])
  }),

  http.delete('/v1/songs/:id', ({ params }) => {
    const index = db.findIndex(s => s.id === Number(params.id))
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    db.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),
]
