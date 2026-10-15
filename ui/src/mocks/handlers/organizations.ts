import { http, HttpResponse } from 'msw'
import { db, getMockCurrentContributorId } from './contributors'

export interface OrganizationResponse {
  id: number
  slug: string
  name: string
  inviteToken?: string
}

export const organizationsDb: OrganizationResponse[] = [
  { id: 1, slug: 'default', name: 'Default Organization' },
]

/** Exactly one of inviteToken/organizationSlug must resolve — mirrors resolveOrganization on both real backends. */
function resolveOrg(body: { organizationSlug?: string; inviteToken?: string }): OrganizationResponse | null {
  if (body.inviteToken) return organizationsDb.find(o => o.inviteToken === body.inviteToken) ?? null
  return organizationsDb.find(o => o.slug === body.organizationSlug) ?? null
}

export const organizationHandlers = [
  http.get('/api/v1/organizations', () => {
    return HttpResponse.json(organizationsDb.map(({ inviteToken, ...rest }) => rest))
  }),

  http.get('/api/v1/organizations/by-invite/:token', ({ params }) => {
    const org = organizationsDb.find(o => o.inviteToken === params.token)
    if (!org) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    return HttpResponse.json({ id: org.id, slug: org.slug, name: org.name })
  }),

  http.get('/api/v1/organizations/invite-link', () => {
    const acting = db.find(c => c.id === getMockCurrentContributorId())
    if (!acting?.isOrgAdmin) {
      return HttpResponse.json({ title: 'Forbidden', detail: 'Only an organization admin can do this' }, { status: 403 })
    }
    return HttpResponse.json({ inviteToken: organizationsDb[0]?.inviteToken ?? null })
  }),

  http.post('/api/v1/organizations/invite-link', () => {
    const acting = db.find(c => c.id === getMockCurrentContributorId())
    if (!acting?.isOrgAdmin) {
      return HttpResponse.json({ title: 'Forbidden', detail: 'Only an organization admin can do this' }, { status: 403 })
    }
    const hex = () => Math.floor(Math.random() * 16).toString(16)
    organizationsDb[0].inviteToken = Array.from({ length: 32 }, hex).join('')
    return HttpResponse.json({ inviteToken: organizationsDb[0].inviteToken })
  }),

  http.delete('/api/v1/organizations/invite-link', () => {
    const acting = db.find(c => c.id === getMockCurrentContributorId())
    if (!acting?.isOrgAdmin) {
      return HttpResponse.json({ title: 'Forbidden', detail: 'Only an organization admin can do this' }, { status: 403 })
    }
    delete organizationsDb[0].inviteToken
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/api/v1/register', async ({ request }) => {
    const body = await request.json() as {
      name: string
      email?: string
      avatarUrl?: string
      organizationSlug?: string
      inviteToken?: string
    }

    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    if (!body.organizationSlug?.trim() && !body.inviteToken?.trim()) {
      return HttpResponse.json({ message: 'Either organizationSlug or inviteToken is required' }, { status: 400 })
    }
    const org = resolveOrg(body)
    if (!org) {
      return HttpResponse.json(
        { title: 'Not Found', detail: body.inviteToken ? 'Invalid or expired invite link' : `Organization not found: ${body.organizationSlug}` },
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
      isOrgAdmin: false,
    }
    db.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),
]
