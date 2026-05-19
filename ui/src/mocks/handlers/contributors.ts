import { http, HttpResponse } from 'msw'

interface ContributorResponse {
  id: string
  name: string
  email: string | null
  avatarUrl: string | null
  version: number
  createdAt: string
  updatedAt: string
}

let counter = 4
const nextId = () => `cont-${String(counter++).padStart(16, '0')}`

const db: ContributorResponse[] = [
  { id: 'cont-0000000000000001', name: 'Thom Yorke', email: 'thom@example.com', avatarUrl: 'https://i.pravatar.cc/150?u=thom', version: 0, createdAt: '2024-01-01T10:00:00Z', updatedAt: '2024-01-01T10:00:00Z' },
  { id: 'cont-0000000000000002', name: 'Nigel Godrich', email: null, avatarUrl: null, version: 0, createdAt: '2024-01-02T12:00:00Z', updatedAt: '2024-01-02T12:00:00Z' },
  { id: 'cont-0000000000000003', name: 'Jonny Greenwood', email: 'jonny@example.com', avatarUrl: 'https://i.pravatar.cc/150?u=jonny', version: 1, createdAt: '2024-01-03T23:00:00Z', updatedAt: '2024-01-10T01:00:00Z' },
]

const now = () => new Date().toISOString()

export const contributorHandlers = [
  http.get('/api/v1/contributors', ({ request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const name = url.searchParams.get('name')?.toLowerCase()
    const filtered = name ? db.filter(c => c.name.toLowerCase().includes(name)) : db
    const content = filtered.slice(page * size, page * size + size)
    return HttpResponse.json({
      content,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      number: page,
      size,
    })
  }),

  http.post('/api/v1/contributors', async ({ request }) => {
    const body = await request.json() as { name: string; email?: string; avatarUrl?: string }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    const created: ContributorResponse = {
      id: nextId(),
      name: body.name,
      email: body.email ?? null,
      avatarUrl: body.avatarUrl ?? null,
      version: 0,
      createdAt: now(),
      updatedAt: now(),
    }
    db.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.get('/api/v1/contributors/:id', ({ params }) => {
    const contributor = db.find(c => c.id === params.id)
    if (!contributor) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    return HttpResponse.json(contributor)
  }),

  http.put('/api/v1/contributors/:id', async ({ params, request }) => {
    const index = db.findIndex(c => c.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    const body = await request.json() as { name: string; email?: string; avatarUrl?: string }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    db[index] = {
      ...db[index],
      name: body.name,
      email: body.email ?? null,
      avatarUrl: body.avatarUrl ?? null,
      version: db[index].version + 1,
      updatedAt: now(),
    }
    return HttpResponse.json(db[index])
  }),

  http.delete('/api/v1/contributors/:id', ({ params }) => {
    const index = db.findIndex(c => c.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    db.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),
]
