import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ContributorItem } from '../repositories/contributor'
import type { OrganizationItem } from '../repositories/organization'

vi.mock('../repositories/organization', () => ({
  findAllOrganizations: vi.fn(),
  findOrganizationById: vi.fn(),
  findOrganizationBySlug: vi.fn(),
  insertOrganization: vi.fn(),
  saveOrganization: vi.fn(),
}))
vi.mock('../repositories/contributor', () => ({
  findAllContributorsByTenant: vi.fn(),
  findContributorByCognitoSub: vi.fn(),
  findContributorByEmail: vi.fn(),
  saveContributor: vi.fn(),
}))
vi.mock('../cognito', () => ({
  findCognitoSubByEmail: vi.fn(),
}))

import * as organizationRepo from '../repositories/organization'
import * as contributorRepo from '../repositories/contributor'
import * as cognito from '../cognito'
import { ConflictError, NotFoundError, ValidationError } from '../errors'
import {
  addOrganizationContributor,
  createOrganization,
  listOrganizationContributors,
  listOrganizationsForAdmin,
  updateOrganization,
} from './admin'

function org(overrides: Partial<OrganizationItem> = {}): OrganizationItem {
  return { id: 1, slug: 'acme', name: 'ACME Corp', ...overrides }
}

function contributor(overrides: Partial<ContributorItem> = {}): ContributorItem {
  return {
    pk: '1#CONTRIBUTOR', sk: '10', id: 10n, tenantId: 1, name: 'Alice', email: 'alice@acme.com',
    createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(organizationRepo.insertOrganization).mockResolvedValue(true)
  vi.mocked(organizationRepo.saveOrganization).mockImplementation(async (item) => item)
  vi.mocked(contributorRepo.saveContributor).mockImplementation(async (item) => item)
  vi.mocked(contributorRepo.findContributorByEmail).mockResolvedValue(undefined)
  vi.mocked(contributorRepo.findContributorByCognitoSub).mockResolvedValue(undefined)
  vi.mocked(cognito.findCognitoSubByEmail).mockResolvedValue(undefined)
})

describe('listOrganizationsForAdmin', () => {
  it('returns every organization, id-ordered, including allowedDomain', async () => {
    vi.mocked(organizationRepo.findAllOrganizations).mockResolvedValue([
      org({ id: 2, slug: 'beta', name: 'Beta', allowedDomain: 'beta.io' }),
      org(),
    ])

    const result = await listOrganizationsForAdmin()

    expect(result).toEqual([
      { id: 1, slug: 'acme', name: 'ACME Corp', allowedDomain: undefined },
      { id: 2, slug: 'beta', name: 'Beta', allowedDomain: 'beta.io' },
    ])
  })
})

describe('createOrganization', () => {
  beforeEach(() => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(undefined)
    vi.mocked(organizationRepo.findAllOrganizations).mockResolvedValue([org({ id: 3 }), org({ id: 7 })])
  })

  it('allocates max(id)+1 and normalizes slug/domain', async () => {
    const result = await createOrganization({ slug: ' New-Org ', name: ' New Org ', allowedDomain: '@New-Org.com' })

    expect(result).toEqual({ id: 8, slug: 'new-org', name: 'New Org', allowedDomain: 'new-org.com' })
    expect(organizationRepo.insertOrganization).toHaveBeenCalledWith({ id: 8, slug: 'new-org', name: 'New Org', allowedDomain: 'new-org.com' })
  })

  it('starts at id 1 when the directory is empty and leaves allowedDomain unset for blank input', async () => {
    vi.mocked(organizationRepo.findAllOrganizations).mockResolvedValue([])

    const result = await createOrganization({ slug: 'first', name: 'First', allowedDomain: '  ' })

    expect(result.id).toBe(1)
    expect(result.allowedDomain).toBeUndefined()
  })

  it('retries with a fresh id when the insert loses a race', async () => {
    vi.mocked(organizationRepo.insertOrganization).mockResolvedValueOnce(false).mockResolvedValueOnce(true)
    vi.mocked(organizationRepo.findAllOrganizations)
      .mockResolvedValueOnce([org({ id: 7 })])
      .mockResolvedValueOnce([org({ id: 7 }), org({ id: 8 })])

    const result = await createOrganization({ slug: 'racy', name: 'Racy' })

    expect(result.id).toBe(9)
    expect(organizationRepo.insertOrganization).toHaveBeenCalledTimes(2)
  })

  it('gives up with ConflictError after repeated id collisions', async () => {
    vi.mocked(organizationRepo.insertOrganization).mockResolvedValue(false)

    await expect(createOrganization({ slug: 'racy', name: 'Racy' })).rejects.toThrow(ConflictError)
    expect(organizationRepo.insertOrganization).toHaveBeenCalledTimes(3)
  })

  it('rejects a slug that is already taken', async () => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(org())

    await expect(createOrganization({ slug: 'acme', name: 'Dup' })).rejects.toThrow(ConflictError)
    expect(organizationRepo.insertOrganization).not.toHaveBeenCalled()
  })

  it.each(['Has Space', 'under_score', '-leading', 'double--hyphen', 'ünïcode'])('rejects malformed slug %s', async (slug) => {
    await expect(createOrganization({ slug, name: 'X' })).rejects.toThrow(ValidationError)
  })

  it('rejects a malformed allowedDomain', async () => {
    await expect(createOrganization({ slug: 'ok', name: 'X', allowedDomain: 'not a domain' })).rejects.toThrow(ValidationError)
  })
})

describe('updateOrganization', () => {
  it('renames and clears allowedDomain when blank', async () => {
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(org({ allowedDomain: 'acme.com' }))

    const result = await updateOrganization(1, { name: 'ACME Inc', allowedDomain: '' })

    expect(result).toEqual({ id: 1, slug: 'acme', name: 'ACME Inc', allowedDomain: undefined })
    expect(organizationRepo.saveOrganization).toHaveBeenCalledWith(expect.objectContaining({ name: 'ACME Inc', allowedDomain: undefined }))
  })

  it('throws NotFoundError for an unknown or non-positive id', async () => {
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(undefined)

    await expect(updateOrganization(99, { name: 'X' })).rejects.toThrow(NotFoundError)
    await expect(updateOrganization(NaN, { name: 'X' })).rejects.toThrow(NotFoundError)
    expect(organizationRepo.findOrganizationById).toHaveBeenCalledTimes(1)
  })
})

describe('listOrganizationContributors', () => {
  it('lists live contributors by name with their link status', async () => {
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(org())
    vi.mocked(contributorRepo.findAllContributorsByTenant).mockResolvedValue([
      contributor({ name: 'Zed', cognitoSub: 'sub-z' }),
      contributor({ id: 11n, sk: '11', name: 'Amy' }),
      contributor({ id: 12n, sk: '12', name: 'Gone', deletedAt: '2026-02-01T00:00:00Z' }),
    ])

    const result = await listOrganizationContributors(1)

    expect(result.map((c) => [c.name, c.linked])).toEqual([['Amy', false], ['Zed', true]])
  })
})

describe('addOrganizationContributor', () => {
  beforeEach(() => {
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(org())
  })

  it('creates an unlinked contributor when the email has no Cognito account yet', async () => {
    const result = await addOrganizationContributor(1, { name: ' Bob ', email: ' Bob@Acme.com ' })

    expect(result).toMatchObject({ name: 'Bob', email: 'bob@acme.com', linked: false })
    expect(contributorRepo.saveContributor).toHaveBeenCalledWith(expect.objectContaining({ tenantId: 1, pk: '1#CONTRIBUTOR', cognitoSub: undefined }))
  })

  it('links the existing Cognito account for that email immediately', async () => {
    vi.mocked(cognito.findCognitoSubByEmail).mockResolvedValue('sub-bob')

    const result = await addOrganizationContributor(1, { name: 'Bob', email: 'bob@acme.com' })

    expect(result.linked).toBe(true)
    expect(contributorRepo.saveContributor).toHaveBeenCalledWith(expect.objectContaining({ cognitoSub: 'sub-bob' }))
  })

  it('refuses when that account is already linked to a live contributor', async () => {
    vi.mocked(cognito.findCognitoSubByEmail).mockResolvedValue('sub-bob')
    vi.mocked(contributorRepo.findContributorByCognitoSub).mockResolvedValue(contributor({ tenantId: 2, cognitoSub: 'sub-bob' }))

    await expect(addOrganizationContributor(1, { name: 'Bob', email: 'bob@acme.com' })).rejects.toThrow(ConflictError)
    expect(contributorRepo.saveContributor).not.toHaveBeenCalled()
  })

  it('refuses a duplicate email within the organization but ignores a soft-deleted one', async () => {
    vi.mocked(contributorRepo.findContributorByEmail).mockResolvedValueOnce(contributor())
    await expect(addOrganizationContributor(1, { name: 'Bob', email: 'alice@acme.com' })).rejects.toThrow(ConflictError)

    vi.mocked(contributorRepo.findContributorByEmail).mockResolvedValueOnce(contributor({ deletedAt: '2026-02-01T00:00:00Z' }))
    await expect(addOrganizationContributor(1, { name: 'Bob', email: 'alice@acme.com' })).resolves.toMatchObject({ linked: false })
  })

  it('applies the allowedDomain gate so the record could actually be linked later', async () => {
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(org({ allowedDomain: 'acme.com' }))

    await expect(addOrganizationContributor(1, { name: 'Bob', email: 'bob@gmail.com' })).rejects.toThrow(ValidationError)
    expect(cognito.findCognitoSubByEmail).not.toHaveBeenCalled()
  })

  it('rejects a malformed email and an unknown organization', async () => {
    await expect(addOrganizationContributor(1, { name: 'Bob', email: 'nope' })).rejects.toThrow(ValidationError)

    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(undefined)
    await expect(addOrganizationContributor(5, { name: 'Bob', email: 'bob@acme.com' })).rejects.toThrow(NotFoundError)
  })
})
