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

// Licences (Lambda API only — see ui/amplify/lib/services/license.ts). One unused key is seeded
// so dev/e2e can exercise activation; the default organization starts without a licence and
// enforcement off, exactly like an existing organization after the feature is deployed.
interface MockLicense { key: string; maxMembers: number; validUntil: string; organizationId?: number }
export const licensesDb: MockLicense[] = [
  { key: 'ORG-2222-3333-4444', maxMembers: 10, validUntil: '2099-12-31' },
]

function licenseStatus(orgId: number) {
  const lic = licensesDb.find(l => l.organizationId === orgId)
  return {
    state: lic ? 'active' : 'none',
    enforced: false,
    ...(lic ? { validUntil: lic.validUntil, maxMembers: lic.maxMembers } : {}),
    members: db.length,
  }
}

export const organizationHandlers = [
  http.get('/api/v1/organizations/license', () => HttpResponse.json(licenseStatus(organizationsDb[0].id))),

  http.post('/api/v1/organizations/license', async ({ request }) => {
    const acting = db.find(c => c.id === getMockCurrentContributorId())
    if (!acting?.isOrgAdmin) {
      return HttpResponse.json({ title: 'Forbidden', detail: 'Only an organization admin can activate a licence' }, { status: 403 })
    }
    const { key } = await request.json() as { key?: string }
    const c = (key ?? '').toUpperCase().replace(/[^0-9A-Z]/g, '')
    const norm = c.length === 15 ? `${c.slice(0, 3)}-${c.slice(3, 7)}-${c.slice(7, 11)}-${c.slice(11)}` : ''
    if (!/^ORG(-[2-9ABCDEFGHJKMNPQRSTUVWXYZ]{4}){3}$/.test(norm)) {
      return HttpResponse.json({ title: 'Bad Request', detail: 'key: format ORG-XXXX-XXXX-XXXX' }, { status: 400 })
    }
    const lic = licensesDb.find(l => l.key === norm)
    if (!lic) return HttpResponse.json({ title: 'Not Found', detail: 'Licence key not found' }, { status: 404 })
    if (lic.organizationId !== undefined) {
      return HttpResponse.json({ title: 'Conflict', detail: 'This licence key has already been used' }, { status: 409 })
    }
    lic.organizationId = organizationsDb[0].id
    return HttpResponse.json(licenseStatus(organizationsDb[0].id))
  }),

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
