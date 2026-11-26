import { GetCommand, PutCommand, QueryCommand, ScanCommand } from '@aws-sdk/lib-dynamodb'
import { ddb } from '../dynamodb'

const TABLE = () => process.env.DYNAMODB_TABLE_LICENSES ?? ''

/**
 * An organization licence. Issued by the operator in orgasm-admin (key, member cap, validity),
 * activated by an org admin entering the key (`organizationId`/`activatedAt` set once, never
 * cleared — a key can't be moved to another organization). Not tenant-partitioned: `key` is the
 * partition key, and the sparse `byOrganization` GSI holds only activated licences.
 *
 * orgasm-admin keeps a copy of this file (backend/src/orgasm/repositories/license.ts) — change both.
 */
export interface LicenseItem {
  /** `ORG-XXXX-XXXX-XXXX` (unambiguous characters, see services/license.ts's LICENSE_KEY) */
  key: string
  /** Operator's note, e.g. the invoice number. */
  note?: string
  maxMembers: number
  /** Last valid day, YYYY-MM-DD (inclusive). */
  validUntil: string
  createdAt: string
  organizationId?: number
  activatedAt?: string
}

export async function findLicenseByKey(key: string): Promise<LicenseItem | undefined> {
  const res = await ddb.send(new GetCommand({ TableName: TABLE(), Key: { key } }))
  return res.Item as LicenseItem | undefined
}

/** All licences an organization ever activated, newest activation first. */
export async function findLicensesByOrganization(organizationId: number): Promise<LicenseItem[]> {
  const res = await ddb.send(
    new QueryCommand({
      TableName: TABLE(),
      IndexName: 'byOrganization',
      KeyConditionExpression: 'organizationId = :o',
      ExpressionAttributeValues: { ':o': organizationId },
      ScanIndexForward: false,
    }),
  )
  return (res.Items as LicenseItem[]) ?? []
}

/** Small, operator-managed table — a Scan is fine (orgasm-admin's licence list). */
export async function findAllLicenses(): Promise<LicenseItem[]> {
  const res = await ddb.send(new ScanCommand({ TableName: TABLE() }))
  return (res.Items as LicenseItem[]) ?? []
}

/**
 * Binds the licence to the organization — refused (false) when the key was activated in the
 * meantime (two org admins racing for the same key), so a key can never serve two organizations.
 */
export async function activateLicense(item: LicenseItem, organizationId: number, activatedAt: string): Promise<boolean> {
  try {
    await ddb.send(
      new PutCommand({
        TableName: TABLE(),
        Item: { ...item, organizationId, activatedAt },
        ConditionExpression: 'attribute_exists(#k) AND attribute_not_exists(organizationId)',
        ExpressionAttributeNames: { '#k': 'key' },
      }),
    )
    return true
  } catch (e) {
    if (e instanceof Error && e.name === 'ConditionalCheckFailedException') return false
    throw e
  }
}
