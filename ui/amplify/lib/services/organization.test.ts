import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ContributorItem } from '../repositories/contributor'
import type { OrganizationItem } from '../repositories/organization'
import { ForbiddenError, NotFoundError } from '../errors'

vi.mock('../repositories/contributor', () => ({
  findContributorById: vi.fn(),
}))
vi.mock('../repositories/organization', () => ({
  findAllOrganizations: vi.fn(),
  findOrganizationById: vi.fn(),
  findOrganizationByInviteToken: vi.fn(),
  findOrganizationBySlug: vi.fn(),
  saveOrganization: vi.fn(),
}))

import * as contributorRepo from '../repositories/contributor'
import * as organizationRepo from '../repositories/organization'
import { createInviteLink, getInviteLink, getOrganizationByInviteToken, revokeInviteLink } from './organization'

const TENANT_ID = 1

function org(overrides: Partial<OrganizationItem> = {}): OrganizationItem {
  return { id: TENANT_ID, slug: 'acme', name: 'ACME Corp', ...overrides }
}

function contributor(overrides: Partial<ContributorItem> = {}): ContributorItem {
  return {
    pk: `${TENANT_ID}#CONTRIBUTOR`,
    sk: '1',
    id: 1n,
    tenantId: TENANT_ID,
    name: 'Admin',
    createdAt: 'now',
    updatedAt: 'now',
    ...overrides,
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(organizationRepo.saveOrganization).mockImplementation(async (item) => item)
})

describe('getOrganizationByInviteToken', () => {
  it('returns the org summary for a valid token', async () => {
    vi.mocked(organizationRepo.findOrganizationByInviteToken).mockResolvedValue(org({ inviteToken: 'tok-123' }))

    const result = await getOrganizationByInviteToken('tok-123')

    expect(result).toEqual({ id: TENANT_ID, slug: 'acme', name: 'ACME Corp' })
  })

  it('throws NotFoundError for an unknown token', async () => {
    vi.mocked(organizationRepo.findOrganizationByInviteToken).mockResolvedValue(undefined)

    await expect(getOrganizationByInviteToken('bad-token')).rejects.toThrow(NotFoundError)
  })
})

describe('getInviteLink / createInviteLink / revokeInviteLink', () => {
  it('getInviteLink returns null when no link has been generated yet', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor({ orgAdmin: true }))
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(org())

    await expect(getInviteLink(TENANT_ID, 1n)).resolves.toEqual({ inviteToken: null })
  })

  it('getInviteLink throws ForbiddenError when the caller is not an org admin', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor({ orgAdmin: false }))

    await expect(getInviteLink(TENANT_ID, 1n)).rejects.toThrow(ForbiddenError)
  })

  it('createInviteLink generates and persists a new token', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor({ orgAdmin: true }))
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(org());

    const result = await createInviteLink(TENANT_ID, 1n)

    expect(result.inviteToken).toMatch(/^[0-9a-f]{32}$/)
    expect(organizationRepo.saveOrganization).toHaveBeenCalledWith(expect.objectContaining({ inviteToken: result.inviteToken }))
  })

  it('createInviteLink throws ForbiddenError when the caller is not an org admin', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor({ orgAdmin: false }))

    await expect(createInviteLink(TENANT_ID, 1n)).rejects.toThrow(ForbiddenError)
    expect(organizationRepo.saveOrganization).not.toHaveBeenCalled()
  })

  it('revokeInviteLink clears the token', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor({ orgAdmin: true }))
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(org({ inviteToken: 'tok-123' }))

    await revokeInviteLink(TENANT_ID, 1n)

    expect(organizationRepo.saveOrganization).toHaveBeenCalledWith(expect.not.objectContaining({ inviteToken: expect.anything() }))
  })

  it('throws NotFoundError when the caller org itself cannot be found', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor({ orgAdmin: true }))
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(undefined)

    await expect(getInviteLink(TENANT_ID, 1n)).rejects.toThrow(NotFoundError)
  })
})
