import { http, HttpResponse } from 'msw'
import { db } from './contributors'

export interface OrganizationResponse {
  id: number
  slug: string
  name: string
}

export const organizationsDb: OrganizationResponse[] = [
  { id: 1, slug: 'default', name: 'Default Organization' },
]

export const organizationHandlers = [
  http.get('/api/v1/organizations', () => {
    return HttpResponse.json(organizationsDb)
  }),

  http.post('/api/v1/register', async ({ request }) => {
    const body = await request.json() as {
      name: string
      email?: string
      avatarUrl?: string
      organizationSlug: string
    }

    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    if (!body.organizationSlug?.trim()) {
      return HttpResponse.json({ message: 'organizationSlug is required' }, { status: 400 })
    }
    const org = organizationsDb.find(o => o.slug === body.organizationSlug)
    if (!org) {
      return HttpResponse.json(
        { title: 'Not Found', detail: `Organization not found: ${body.organizationSlug}` },
        { status: 404 },
      )
    }

    const hex = () => Math.floor(Math.random() * 16).toString(16)
    const id = `cont-${Array.from({ length: 16 }, hex).join('')}`
    const now = new Date().toISOString()
    const created = {
      id,
      name: body.name,
      email: body.email ?? null,
      avatarUrl: body.avatarUrl ?? null,
      version: 0,
      createdAt: now,
      updatedAt: now,
    }
    db.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
]
