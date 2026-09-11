import { GetCommand, PutCommand, QueryCommand } from '@aws-sdk/lib-dynamodb'
import { ddb, VersionConflictError } from '../dynamodb'

/** Partition key convention shared by every tenant-scoped table: `<tenantId>#<ENTITY_TYPE>`. */
export function partitionKey(tenantId: number, entityType: string): string {
  return `${tenantId}#${entityType}`
}

/**
 * `@aws-sdk/lib-dynamodb`'s default (un)marshaller auto-detects: a DynamoDB N value that fits
 * safely in a JS `number` comes back as `number`, one that doesn't (our ids always don't — see
 * idGenerator.ts, the id scheme produces ~60-bit values) comes back as `bigint`. Every id-like
 * field is typed `bigint` in this codebase regardless, so coerce defensively on read rather than
 * trust the value's runtime type — this also matters in tests against small hand-picked ids.
 */
export function toBigInt(value: unknown): bigint {
  return typeof value === 'bigint' ? value : BigInt(value as number | string)
}

export async function getItem<T>(tableName: string, pk: string, sk: string): Promise<T | undefined> {
  const res = await ddb.send(new GetCommand({ TableName: tableName, Key: { pk, sk } }))
  return res.Item as T | undefined
}

/**
 * Always writes the FULL item (load-mutate-save, never a partial patch) with an optimistic-lock
 * condition on `version` — the Document Client has no VersionedRecordExtension equivalent, and a
 * partial write was exactly the bug documented on NominationDynamoRepository.declinePendingByPlaylistId
 * in the Java source (a partial item has no version, read as "doesn't exist yet", failing its
 * conditional check against an item that already exists). Requiring the whole object here avoids
 * that failure mode by construction.
 */
export async function saveItem<T extends { version?: number }>(tableName: string, item: T): Promise<T> {
  const previousVersion = item.version
  const toWrite = { ...item, version: (previousVersion ?? 0) + 1 }
  try {
    await ddb.send(
      new PutCommand({
        TableName: tableName,
        Item: toWrite,
        ConditionExpression: previousVersion === undefined ? 'attribute_not_exists(#v)' : '#v = :expectedVersion',
        ExpressionAttributeNames: { '#v': 'version' },
        ExpressionAttributeValues: previousVersion === undefined ? undefined : { ':expectedVersion': previousVersion },
      }),
    )
  } catch (e) {
    if (e instanceof Error && e.name === 'ConditionalCheckFailedException') {
      throw new VersionConflictError()
    }
    throw e
  }
  return toWrite
}

/** No version attribute — for hard-delete-only entities (Guess), matching its Java counterpart. */
export async function putItemNoVersion<T>(tableName: string, item: T): Promise<T> {
  await ddb.send(new PutCommand({ TableName: tableName, Item: item as Record<string, unknown> }))
  return item
}

async function queryAllPages<T>(input: {
  TableName: string
  IndexName?: string
  KeyConditionExpression: string
  ExpressionAttributeValues: Record<string, unknown>
}): Promise<T[]> {
  const items: T[] = []
  let ExclusiveStartKey: Record<string, unknown> | undefined
  do {
    const res = await ddb.send(new QueryCommand({ ...input, ExclusiveStartKey }))
    items.push(...((res.Items as T[]) ?? []))
    ExclusiveStartKey = res.LastEvaluatedKey
  } while (ExclusiveStartKey)
  return items
}

/** Bounded to one tenant's partition — a Query, not a table-wide Scan. */
export function queryByPartitionKey<T>(tableName: string, pk: string): Promise<T[]> {
  return queryAllPages<T>({
    TableName: tableName,
    KeyConditionExpression: 'pk = :pk',
    ExpressionAttributeValues: { ':pk': pk },
  })
}

export function queryIndex<T>(
  tableName: string,
  indexName: string,
  keyConditionExpression: string,
  values: Record<string, unknown>,
): Promise<T[]> {
  return queryAllPages<T>({
    TableName: tableName,
    IndexName: indexName,
    KeyConditionExpression: keyConditionExpression,
    ExpressionAttributeValues: values,
  })
}
