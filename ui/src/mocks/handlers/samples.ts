import { http, HttpResponse } from 'msw'
import type { SampleResponse, SampleStatus } from '@pi2/anchor-client'

let nextId = 4
const makeId = () => `smpl-${String(nextId++).padStart(4, '0')}`

const store: SampleResponse[] = [
  {
    id: 'smpl-0001',
    name: 'Alpha Widget',
    description: 'A simple string description',
    email: 'alpha@example.com',
    quantity: 10,
    largeNumber: 9999999999,
    rating: 8.5,
    price: 19.99,
    active: true,
    birthDate: '1990-06-15',
    scheduledAt: '2024-12-01T09:00:00',
    status: 'ACTIVE' as SampleStatus,
    notes: 'First seeded record',
    version: 0,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 'smpl-0002',
    name: 'Beta Gadget',
    description: null,
    email: null,
    quantity: 0,
    largeNumber: 0,
    rating: 0.0,
    price: null,
    active: false,
    birthDate: null,
    scheduledAt: null,
    status: 'DRAFT' as SampleStatus,
    notes: null,
    version: 0,
    createdAt: '2024-02-01T00:00:00Z',
    updatedAt: '2024-02-01T00:00:00Z',
  },
  {
    id: 'smpl-0003',
    name: 'Gamma Device',
    description: 'Archived item',
    email: 'gamma@example.com',
    quantity: 9999,
    largeNumber: 1234567890123,
    rating: 10.0,
    price: 9999.9999,
    active: true,
    birthDate: '2000-01-01',
    scheduledAt: '2025-06-15T14:30:00',
    status: 'ARCHIVED' as SampleStatus,
    notes: 'Long notes field for text type testing',
    version: 3,
    createdAt: '2024-03-01T00:00:00Z',
    updatedAt: '2024-06-01T00:00:00Z',
  },
]

function paginate(items: SampleResponse[], page: number, size: number) {
  const totalElements = items.length
  const totalPages = Math.ceil(totalElements / size) || 1
  const start = page * size
  return {
    content: items.slice(start, start + size),
    page: { size, number: page, totalElements, totalPages },
  }
}

export const sampleHandlers = [
  http.get('/api/v1/samples', ({ request }) => {
    const url = new URL(request.url)
    const name = url.searchParams.get('name')?.toLowerCase()
    const status = url.searchParams.get('status') as SampleStatus | null
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '10')

    let items = store.filter(s => s.id)
    if (name) items = items.filter(s => s.name?.toLowerCase().includes(name))
    if (status) items = items.filter(s => s.status === status)

    return HttpResponse.json(paginate(items, page, size))
  }),

  http.get('/api/v1/samples/:id', ({ params }) => {
    const s = store.find(x => x.id === params.id)
    if (!s) return HttpResponse.json({ status: 404, detail: 'Not found' }, { status: 404 })
    return HttpResponse.json(s)
  }),

  http.post('/api/v1/samples', async ({ request }) => {
    const body = await request.json() as Partial<SampleResponse>
    if (!body.name?.trim()) {
      return HttpResponse.json({ status: 400, errors: { name: 'must not be blank' } }, { status: 400 })
    }
    const now = new Date().toISOString()
    const created: SampleResponse = {
      id: makeId(),
      name: body.name.trim(),
      description: body.description ?? null,
      email: body.email ?? null,
      quantity: body.quantity ?? 0,
      largeNumber: body.largeNumber ?? 0,
      rating: body.rating ?? 0,
      price: body.price ?? null,
      active: body.active ?? true,
      birthDate: body.birthDate ?? null,
      scheduledAt: body.scheduledAt ?? null,
      status: body.status ?? 'DRAFT',
      notes: body.notes ?? null,
      version: 0,
      createdAt: now,
      updatedAt: now,
    }
    store.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put('/api/v1/samples/:id', async ({ params, request }) => {
    const idx = store.findIndex(x => x.id === params.id)
    if (idx === -1) return HttpResponse.json({ status: 404, detail: 'Not found' }, { status: 404 })
    const body = await request.json() as Partial<SampleResponse>
    const updated: SampleResponse = {
      ...store[idx],
      ...body,
      id: store[idx].id,
      updatedAt: new Date().toISOString(),
      version: (store[idx].version ?? 0) + 1,
    }
    store[idx] = updated
    return HttpResponse.json(updated)
  }),

  http.delete('/api/v1/samples/:id', ({ params }) => {
    const idx = store.findIndex(x => x.id === params.id)
    if (idx === -1) return HttpResponse.json({ status: 404, detail: 'Not found' }, { status: 404 })
    store.splice(idx, 1)
    return new HttpResponse(null, { status: 204 })
  }),
]
