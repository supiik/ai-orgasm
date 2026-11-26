import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ContributorItem } from '../repositories/contributor'
import type { LicenseItem } from '../repositories/license'

const store = vi.hoisted(() => ({
  licenses: new Map<string, LicenseItem>(),
  members: [] as Partial<ContributorItem>[],
}))

vi.mock('../repositories/license', () => ({
  findLicenseByKey: vi.fn(async (key: string) => store.licenses.get(key)),
  findLicensesByOrganization: vi.fn(async (org: number) =>
    [...store.licenses.values()].filter((l) => l.organizationId === org).sort((a, b) => (b.activatedAt ?? '').localeCompare(a.activatedAt ?? ''))),
  activateLicense: vi.fn(async (item: LicenseItem, organizationId: number, activatedAt: string) => {
    const cur = store.licenses.get(item.key)
    if (!cur || cur.organizationId !== undefined) return false
    store.licenses.set(item.key, { ...cur, organizationId, activatedAt })
    return true
  }),
}))
vi.mock('../repositories/contributor', () => ({
  findAllContributorsByTenant: vi.fn(async (tenantId: number) => store.members.filter((m) => m.tenantId === tenantId)),
  findContributorById: vi.fn(async (tenantId: number, id: bigint) => store.members.find((m) => m.tenantId === tenantId && m.id === id)),
}))

const {
  activateOrganizationLicense, addDays, assertMemberCapacity, assertWritable, getLicenseStatus, licenseState, normLicenseKey,
} = await import('./license')

const lic = (key: string, over: Partial<LicenseItem> = {}): LicenseItem => ({ key, maxMembers: 2, validUntil: '2026-12-31', createdAt: '2026-11-01T00:00:00Z', ...over })

beforeEach(() => {
  store.licenses = new Map([
    ['ORG-AAAA-BBBB-CCCC', lic('ORG-AAAA-BBBB-CCCC')],
    ['ORG-USED-2222-3333', lic('ORG-USED-2222-3333', { organizationId: 9, activatedAt: '2026-11-02T00:00:00Z' })],
  ])
  store.members = [
    { tenantId: 1, id: 1n, orgAdmin: true },
    { tenantId: 1, id: 2n },
    { tenantId: 1, id: 3n, deletedAt: '2026-10-01' },
  ]
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-11-26T10:00:00Z'))
  process.env.LICENSE_ENFORCEMENT = 'on'
})
afterEach(() => {
  vi.useRealTimers()
  delete process.env.LICENSE_ENFORCEMENT
})

describe('licenseState', () => {
  it('active through validUntil, then 14 days grace, then expired', () => {
    const l = lic('ORG-AAAA-BBBB-CCCC', { validUntil: '2026-11-30' })
    expect(licenseState(l, '2026-11-30')).toBe('active')
    expect(licenseState(l, '2026-12-01')).toBe('grace')
    expect(licenseState(l, '2026-12-14')).toBe('grace')
    expect(licenseState(l, '2026-12-15')).toBe('expired')
    expect(licenseState(undefined, '2026-11-30')).toBe('none')
  })
  it('addDays crosses months and years', () => {
    expect(addDays('2026-12-25', 14)).toBe('2027-01-08')
  })
})

describe('activateOrganizationLicense', () => {
  it('org admin activates a free key; status reports cap and live members', async () => {
    const r = await activateOrganizationLicense(1, 1n, ' org-aaaa-bbbb-cccc ')
    expect(r).toEqual({ state: 'active', enforced: true, validUntil: '2026-12-31', graceUntil: undefined, maxMembers: 2, members: 2 })
    expect(store.licenses.get('ORG-AAAA-BBBB-CCCC')?.organizationId).toBe(1)
  })
  it('only an org admin', async () => {
    await expect(activateOrganizationLicense(1, 2n, 'ORG-AAAA-BBBB-CCCC')).rejects.toThrow('Only an organization admin')
  })
  it('bad format 400, unknown 404, used elsewhere 409', async () => {
    await expect(activateOrganizationLicense(1, 1n, 'O-AAAA-BBBB-CCCC')).rejects.toThrow('format')
    await expect(activateOrganizationLicense(1, 1n, 'ORG-ZZZZ-ZZZZ-ZZZZ')).rejects.toThrow('not found')
    await expect(activateOrganizationLicense(1, 1n, 'ORG-USED-2222-3333')).rejects.toThrow('already been used')
  })
})

describe('enforcement', () => {
  it('no licence: writes refused, joining refused', async () => {
    await expect(assertWritable(1)).rejects.toThrow('no licence')
    await expect(assertMemberCapacity(1)).rejects.toThrow('no valid licence')
  })
  it('active: writes allowed; cap counts live members only', async () => {
    store.licenses.set('ORG-AAAA-BBBB-CCCC', lic('ORG-AAAA-BBBB-CCCC', { organizationId: 1, activatedAt: '2026-11-03T00:00:00Z' }))
    await expect(assertWritable(1)).resolves.toBeUndefined()
    await expect(assertMemberCapacity(1)).rejects.toThrow('at most 2 members')
    store.members.pop()
    store.members.pop()
    await expect(assertMemberCapacity(1)).resolves.toBeUndefined()
  })
  it('grace: still writable, status shows the grace end', async () => {
    store.licenses.set('ORG-AAAA-BBBB-CCCC', lic('ORG-AAAA-BBBB-CCCC', { organizationId: 1, activatedAt: '2026-01-01T00:00:00Z', validUntil: '2026-11-20' }))
    await expect(assertWritable(1)).resolves.toBeUndefined()
    expect(await getLicenseStatus(1)).toMatchObject({ state: 'grace', graceUntil: '2026-12-04' })
  })
  it('expired: read-only; the newest activated licence wins', async () => {
    store.licenses.set('ORG-AAAA-BBBB-CCCC', lic('ORG-AAAA-BBBB-CCCC', { organizationId: 1, activatedAt: '2025-01-01T00:00:00Z', validUntil: '2025-12-31' }))
    await expect(assertWritable(1)).rejects.toThrow('expired')
    store.licenses.set('ORG-NEWW-2222-3333', lic('ORG-NEWW-2222-3333', { organizationId: 1, activatedAt: '2026-11-25T00:00:00Z', validUntil: '2027-11-30' }))
    await expect(assertWritable(1)).resolves.toBeUndefined()
  })
  it('LICENSE_ENFORCEMENT off: nothing refused, status still computed', async () => {
    process.env.LICENSE_ENFORCEMENT = 'off'
    await expect(assertWritable(1)).resolves.toBeUndefined()
    await expect(assertMemberCapacity(1)).resolves.toBeUndefined()
    expect(await getLicenseStatus(1)).toMatchObject({ state: 'none', enforced: false, members: 2 })
  })
})

describe('normLicenseKey', () => {
  it('normalises spacing/case and rejects ambiguous characters', () => {
    expect(normLicenseKey(' org aaaa bbbb-cccc ')).toBe('ORG-AAAA-BBBB-CCCC')
    expect(normLicenseKey('ORG-AAAA-BBBB-CCCL')).toBeNull()
    expect(normLicenseKey('SHR-AAAA-BBBB-CCCC')).toBeNull()
    expect(normLicenseKey(42)).toBeNull()
  })
})
