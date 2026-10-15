import { beforeEach, describe, expect, it, vi } from 'vitest'
import { formatId } from '../idGenerator'
import type { ContributorItem } from '../repositories/contributor'
import { ForbiddenError, NotFoundError } from '../errors'

vi.mock('../repositories/contributor', () => ({
  findContributorById: vi.fn(),
  findAllContributorsByTenant: vi.fn(),
  saveContributor: vi.fn(),
}))

import * as contributorRepo from '../repositories/contributor'
import { isOrgAdmin, setOrgAdmin } from './contributor'

const TENANT_ID = 1

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
  vi.mocked(contributorRepo.saveContributor).mockImplementation(async (item) => item)
})

describe('isOrgAdmin', () => {
  it('returns true when the contributor has orgAdmin set', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor({ orgAdmin: true }))

    await expect(isOrgAdmin(TENANT_ID, 1n)).resolves.toBe(true)
  })

  it('returns false when the contributor has no orgAdmin flag', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor())

    await expect(isOrgAdmin(TENANT_ID, 1n)).resolves.toBe(false)
  })

  it('returns false when the contributor does not exist', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(undefined)

    await expect(isOrgAdmin(TENANT_ID, 1n)).resolves.toBe(false)
  })
})

describe('setOrgAdmin', () => {
  it('promotes the target when the acting contributor is already an org admin', async () => {
    const acting = contributor({ id: 1n, orgAdmin: true })
    const target = contributor({ id: 2n, name: 'Bob', orgAdmin: false })
    vi.mocked(contributorRepo.findContributorById).mockImplementation(async (_tenantId, id) =>
      id === 1n ? acting : id === 2n ? target : undefined,
    )

    const result = await setOrgAdmin(TENANT_ID, 1n, formatId('cont', 2n), true)

    expect(result.isOrgAdmin).toBe(true)
    expect(contributorRepo.saveContributor).toHaveBeenCalledWith(expect.objectContaining({ id: 2n, orgAdmin: true }))
  })

  it('throws ForbiddenError when the acting contributor is not an org admin', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor({ id: 1n, orgAdmin: false }))

    await expect(setOrgAdmin(TENANT_ID, 1n, formatId('cont', 2n), true)).rejects.toThrow(ForbiddenError)
    expect(contributorRepo.saveContributor).not.toHaveBeenCalled()
  })

  it('throws ForbiddenError when the acting account is not linked to a contributor', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(undefined)

    await expect(setOrgAdmin(TENANT_ID, 1n, formatId('cont', 2n), true)).rejects.toThrow(ForbiddenError)
  })

  it('throws NotFoundError when the target contributor does not exist', async () => {
    vi.mocked(contributorRepo.findContributorById).mockImplementation(async (_tenantId, id) =>
      id === 1n ? contributor({ id: 1n, orgAdmin: true }) : undefined,
    )

    await expect(setOrgAdmin(TENANT_ID, 1n, formatId('cont', 2n), true)).rejects.toThrow(NotFoundError)
  })
})
