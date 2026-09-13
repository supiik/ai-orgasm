import { http, HttpResponse } from 'msw'
import { db } from './contributors'
import { organizationsDb } from './organizations'

// Mock of the Lambda admin-* functions (ui/amplify/lib/services/admin.ts) at the REST paths
// api-backend.ts's admin client uses. Mirrors the service's validation so AdminView's error
// states are exercisable without a deployed stack. `allowedDomain` lives only here — the public
// organizations handler deliberately never returns it.

interface AdminOrganization { id: number; slug: string; name: string; allowedDomain?: string }

const allowedDomains = new Map<number, string>()
/** Which contributors belong to which mock organization; every seeded contributor is in org 1. */
const membership = new Map<string, number>(db.map(c => [c.id, 1]))
/** Emails that "already have a Cognito account" — adding one of these links immediately. */
const cognitoAccounts = new Set(['thom@example.com', 'jonny@example.com', 'ed@example.com'])

const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const DOMAIN = /^[a-z0-9]+(?:[-.][a-z0-9]+)*\.[a-z]{2,}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const toAdminOrg = (o: { id: number; slug: string; name: string }): AdminOrganization =>
  ({ ...o, allowedDomain: allowedDomains.get(o.id) })

function normalizeDomain(raw: string | undefined): string | undefined | null {
  const trimmed = raw?.trim().replace(/^@/, '').toLowerCase()
  if (!trimmed) return undefined
  return DOMAIN.test(trimmed) ? trimmed : null
}

const error = (status: number, message: string) => HttpResponse.json({ error: message }, { status })

export const adminHandlers = [
  http.get('/api/v1/admin/organizations', () => HttpResponse.json(organizationsDb.map(toAdminOrg))),

  http.post('/api/v1/admin/organizations', async ({ request }) => {
    const body = await request.json() as { slug?: string; name?: string; allowedDomain?: string }
    const slug = body.slug?.trim().toLowerCase() ?? ''
    const name = body.name?.trim() ?? ''
    if (!slug || !name) return error(400, 'slug and name must not be blank')
    if (!SLUG.test(slug)) return error(400, 'slug: lowercase letters, digits and single hyphens only (e.g. my-org)')
    const domain = normalizeDomain(body.allowedDomain)
    if (domain === null) return error(400, 'allowedDomain: must be a domain name such as example.com')
    if (organizationsDb.some(o => o.slug === slug)) return error(409, `Organization slug already in use: ${slug}`)
    const id = organizationsDb.reduce((max, o) => Math.max(max, o.id), 0) + 1
    organizationsDb.push({ id, slug, name })
    if (domain) allowedDomains.set(id, domain)
    return HttpResponse.json(toAdminOrg({ id, slug, name }), { status: 201 })
  }),

  http.put('/api/v1/admin/organizations/:id', async ({ params, request }) => {
    const org = organizationsDb.find(o => o.id === Number(params.id))
    if (!org) return error(404, `Organization not found: ${params.id}`)
    const body = await request.json() as { name?: string; allowedDomain?: string }
    const name = body.name?.trim() ?? ''
    if (!name) return error(400, 'name: must not be blank')
    const domain = normalizeDomain(body.allowedDomain)
    if (domain === null) return error(400, 'allowedDomain: must be a domain name such as example.com')
    org.name = name
    if (domain) allowedDomains.set(org.id, domain)
    else allowedDomains.delete(org.id)
    return HttpResponse.json(toAdminOrg(org))
  }),

  http.get('/api/v1/admin/organizations/:id/contributors', ({ params }) => {
    const id = Number(params.id)
    if (!organizationsDb.some(o => o.id === id)) return error(404, `Organization not found: ${params.id}`)
    const members = db
      .filter(c => membership.get(c.id) === id)
      .map(c => ({ ...c, linked: cognitoAccounts.has(c.email?.toLowerCase() ?? '') }))
      .sort((a, b) => a.name.localeCompare(b.name))
    return HttpResponse.json(members)
  }),

  http.post('/api/v1/admin/organizations/:id/contributors', async ({ params, request }) => {
    const id = Number(params.id)
    const org = organizationsDb.find(o => o.id === id)
    if (!org) return error(404, `Organization not found: ${params.id}`)
    const body = await request.json() as { name?: string; email?: string; avatarUrl?: string }
    const name = body.name?.trim() ?? ''
    const email = body.email?.trim().toLowerCase() ?? ''
    if (!name || !email) return error(400, 'name and email must not be blank')
    if (!EMAIL.test(email)) return error(400, 'email: must be a valid email address')
    const domain = allowedDomains.get(id)
    if (domain && email.slice(email.lastIndexOf('@') + 1) !== domain) {
      return error(400, `Email domain does not match organization "${org.slug}"'s allowed domain (@${domain})`)
    }
    if (db.some(c => membership.get(c.id) === id && c.email?.toLowerCase() === email)) {
      return error(409, `A contributor with email ${email} already exists in organization ${org.slug}`)
    }
    const now = new Date().toISOString()
    const created = {
      id: `cont-${Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join('')}`,
      name,
      email,
      avatarUrl: body.avatarUrl?.trim() || null,
      version: 0,
      createdAt: now,
      updatedAt: now,
    }
    db.push(created)
    membership.set(created.id, id)
    return HttpResponse.json({ ...created, linked: cognitoAccounts.has(email) }, { status: 201 })
  }),
]
