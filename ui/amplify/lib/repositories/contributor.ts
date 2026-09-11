import { getItem, partitionKey, queryByPartitionKey, queryIndex, saveItem } from './base'

const TABLE = () => process.env.DYNAMODB_TABLE_CONTRIBUTORS ?? ''
const ENTITY_TYPE = 'CONTRIBUTOR'

export interface ContributorItem {
  pk: string
  sk: string
  id: bigint
  tenantId: number
  name: string
  email?: string
  avatarUrl?: string
  cognitoSub?: string
  createdAt: string
  updatedAt: string
  deletedAt?: string
  version?: number
}

export function contributorKey(tenantId: number, id: bigint) {
  return { pk: partitionKey(tenantId, ENTITY_TYPE), sk: id.toString() }
}

export async function findContributorById(tenantId: number, id: bigint): Promise<ContributorItem | undefined> {
  const { pk, sk } = contributorKey(tenantId, id)
  return getItem<ContributorItem>(TABLE(), pk, sk)
}

export function findAllContributorsByTenant(tenantId: number): Promise<ContributorItem[]> {
  return queryByPartitionKey<ContributorItem>(TABLE(), partitionKey(tenantId, ENTITY_TYPE))
}

/** Assumes cognitoSub is unique by convention; DynamoDB does not enforce it for a GSI partition key. */
export async function findContributorByCognitoSub(cognitoSub: string): Promise<ContributorItem | undefined> {
  const items = await queryIndex<ContributorItem>(TABLE(), 'byCognitoSub', 'cognitoSub = :sub', { ':sub': cognitoSub })
  return items[0]
}

/** In-memory filter over the tenant's already-fetched Query result — no GSI needed at this table's scale. */
export async function findContributorByEmail(tenantId: number, email: string): Promise<ContributorItem | undefined> {
  const items = await findAllContributorsByTenant(tenantId)
  return items.find((c) => c.email?.toLowerCase() === email.toLowerCase())
}

export function saveContributor(item: ContributorItem): Promise<ContributorItem> {
  return saveItem(TABLE(), item)
}
