import { GetCommand, PutCommand, QueryCommand, ScanCommand } from '@aws-sdk/lib-dynamodb'
import { ddb } from '../dynamodb'

const TABLE = () => process.env.DYNAMODB_TABLE_ORGANIZATIONS ?? ''

/**
 * Not tenant-partitioned — this table IS the tenant directory; `id` is the natural partition
 * key, and equals the `tenantId` used throughout every other table. Plain `number` (not
 * `bigint` like the IdGenerator-produced entity ids) — organization ids are small, assigned
 * directly rather than via the 64-bit id scheme, and the API contract serializes this one as a
 * raw JSON number (see OrganizationResponse.java), not a formatted/prefixed string id.
 */
export interface OrganizationItem {
  id: number
  slug: string
  name: string
  /**
   * When set, registration/linking requires the contributor's email to end in
   * `@<allowedDomain>`. Undefined means unrestricted (mirrors `Organization.allowedDomain` on
   * the Java side). Never returned from `listOrganizations()` — it's a server-side registration
   * gate, not part of the public contract (see `services/organization.ts`'s mapper).
   */
  allowedDomain?: string
}

export async function findOrganizationById(id: number): Promise<OrganizationItem | undefined> {
  const res = await ddb.send(new GetCommand({ TableName: TABLE(), Key: { id } }))
  return res.Item as OrganizationItem | undefined
}

/** Assumes slug is unique by convention; DynamoDB does not enforce it for a GSI partition key. */
export async function findOrganizationBySlug(slug: string): Promise<OrganizationItem | undefined> {
  const res = await ddb.send(
    new QueryCommand({
      TableName: TABLE(),
      IndexName: 'bySlug',
      KeyConditionExpression: 'slug = :slug',
      ExpressionAttributeValues: { ':slug': slug },
    }),
  )
  return (res.Items?.[0] as OrganizationItem) ?? undefined
}

/** Small/global tenant-directory table — a table-wide Scan matches the real app's unpaged findAll(). */
export async function findAllOrganizations(): Promise<OrganizationItem[]> {
  const res = await ddb.send(new ScanCommand({ TableName: TABLE() }))
  return (res.Items as OrganizationItem[]) ?? []
}

export async function saveOrganization(item: OrganizationItem): Promise<OrganizationItem> {
  await ddb.send(new PutCommand({ TableName: TABLE(), Item: item }))
  return item
}
