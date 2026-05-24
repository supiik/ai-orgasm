import { http, HttpResponse } from 'msw'

interface SongResponse {
  id: string
  artist: string
  name: string
  album: string | null
  releaseYear: number | null
  url: string | null
  version: number
  createdAt: string
  updatedAt: string
}

const nextId = () => `song-${Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join('')}`

const db: SongResponse[] = [
  { id: 'song-0af3b7c2d1e8f905', artist: 'Radiohead', name: 'Creep', album: 'Pablo Honey', releaseYear: 1993, url: 'https://www.youtube.com/watch?v=XFkzRNyygfk', version: 0, createdAt: '2024-01-01T10:00:00Z', updatedAt: '2024-01-01T10:00:00Z' },
  { id: 'song-9b2c5e3a7f1d4680', artist: 'Nirvana', name: 'Smells Like Teen Spirit', album: 'Nevermind', releaseYear: 1991, url: null, version: 0, createdAt: '2024-01-02T12:00:00Z', updatedAt: '2024-01-02T12:00:00Z' },
  { id: 'song-c4d7a8e2f3b16509', artist: 'Oasis', name: 'Wonderwall', album: null, releaseYear: null, url: null, version: 1, createdAt: '2024-01-03T23:00:00Z', updatedAt: '2024-01-10T01:00:00Z' },
]

const now = () => new Date().toISOString()

export const songHandlers = [
  http.get('/api/v1/songs', ({ request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const name = url.searchParams.get('name')?.toLowerCase()
    const sort = url.searchParams.get('sort')
    const filtered = name ? db.filter(s => s.name.toLowerCase().includes(name)) : [...db]
    if (sort) {
      const [field, dir] = sort.split(',')
      const key = field as keyof SongResponse
      filtered.sort((a, b) => {
        const av = a[key] ?? '', bv = b[key] ?? ''
        const cmp = av < bv ? -1 : av > bv ? 1 : 0
        return dir === 'desc' ? -cmp : cmp
      })
    }
    const content = filtered.slice(page * size, page * size + size)
    return HttpResponse.json({
      content,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      number: page,
      size,
    })
  }),

  http.post('/api/v1/songs', async ({ request }) => {
    const body = await request.json() as { artist: string; name: string; album?: string; releaseYear?: number; url?: string }
    if (!body.artist?.trim()) {
      return HttpResponse.json({ message: 'Artist is required' }, { status: 400 })
    }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    const created: SongResponse = {
      id: nextId(),
      artist: body.artist,
      name: body.name,
      album: body.album ?? null,
      releaseYear: body.releaseYear ?? null,
      url: body.url ?? null,
      version: 0,
      createdAt: now(),
      updatedAt: now(),
    }
    db.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.get('/api/v1/songs/:id', ({ params }) => {
    const song = db.find(s => s.id === params.id)
    if (!song) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    return HttpResponse.json(song)
  }),

  http.put('/api/v1/songs/:id', async ({ params, request }) => {
    const index = db.findIndex(s => s.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    const body = await request.json() as { artist: string; name: string; album?: string; releaseYear?: number; url?: string }
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
      url: body.url ?? null,
      version: db[index].version + 1,
      updatedAt: now(),
    }
    return HttpResponse.json(db[index])
  }),

  http.delete('/api/v1/songs/:id', ({ params }) => {
    const index = db.findIndex(s => s.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    db.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),
]
