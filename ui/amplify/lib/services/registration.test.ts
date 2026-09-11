import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { OrganizationItem } from '../repositories/organization'

vi.mock('../repositories/organization', () => ({
  findOrganizationBySlug: vi.fn(),
}))
vi.mock('../repositories/contributor', () => ({
  findContributorByEmail: vi.fn(),
  saveContributor: vi.fn(),
}))

import * as organizationRepo from '../repositories/organization'
import * as contributorRepo from '../repositories/contributor'
import { ConflictError, NotFoundError, ValidationError } from '../errors'
import { linkContributor, register } from './registration'

function org(overrides: Partial<OrganizationItem> = {}): OrganizationItem {
  return { id: 1, slug: 'acme', name: 'ACME Corp', ...overrides }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(contributorRepo.saveContributor).mockImplementation(async (item) => item)
})

describe('register', () => {
  it('creates a contributor when the org has no allowedDomain', async () => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(org())

    const result = await register({ organizationSlug: 'acme', name: 'Alice', email: 'alice@gmail.com' })

    expect(result.name).toBe('Alice')
  })

  it('creates a contributor when the email domain matches allowedDomain', async () => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(org({ allowedDomain: 'acme.com' }))

    const result = await register({ organizationSlug: 'acme', name: 'Alice', email: 'alice@ACME.com' })

    expect(result.name).toBe('Alice')
  })

  it('throws ValidationError when the email domain does not match allowedDomain', async () => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(org({ allowedDomain: 'acme.com' }))

    await expect(register({ organizationSlug: 'acme', name: 'Alice', email: 'alice@gmail.com' })).rejects.toThrow(ValidationError)
  })

  it('throws ValidationError when allowedDomain is set and email is missing', async () => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(org({ allowedDomain: 'acme.com' }))

    await expect(register({ organizationSlug: 'acme', name: 'Alice' })).rejects.toThrow(ValidationError)
  })

  it('throws NotFoundError when the organization does not exist', async () => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(undefined)

    await expect(register({ organizationSlug: 'unknown', name: 'Alice' })).rejects.toThrow(NotFoundError)
  })
})

describe('linkContributor', () => {
  it('links when the verified email domain matches allowedDomain', async () => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(org({ allowedDomain: 'acme.com' }))
    vi.mocked(contributorRepo.findContributorByEmail).mockResolvedValue(undefined)

    const result = await linkContributor({ organizationSlug: 'acme', name: 'Alice' }, 'sub-1', 'alice@acme.com')

    expect(result.name).toBe('Alice')
  })

  it('throws ValidationError when the verified email domain does not match allowedDomain', async () => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(org({ allowedDomain: 'acme.com' }))

    await expect(linkContributor({ organizationSlug: 'acme', name: 'Alice' }, 'sub-1', 'alice@gmail.com')).rejects.toThrow(ValidationError)
  })

  it('still enforces the existing-conflicting-account check when allowedDomain is unset', async () => {
    vi.mocked(organizationRepo.findOrganizationBySlug).mockResolvedValue(org())
    vi.mocked(contributorRepo.findContributorByEmail).mockResolvedValue({
      pk: '1#CONTRIBUTOR',
      sk: '1',
      id: 1n,
      tenantId: 1,
      name: 'Alice',
      email: 'alice@gmail.com',
      cognitoSub: 'sub-other',
      createdAt: '2024-01-01T00:00:00.000Z',
      updatedAt: '2024-01-01T00:00:00.000Z',
    })

    await expect(linkContributor({ organizationSlug: 'acme', name: 'Alice' }, 'sub-1', 'alice@gmail.com')).rejects.toThrow(ConflictError)
  })
})
