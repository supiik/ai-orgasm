import { DynamoDBClient } from '@aws-sdk/client-dynamodb'
import { DynamoDBDocumentClient } from '@aws-sdk/lib-dynamodb'

// One client per cold start, reused across warm invocations — mirrors backend-dynamo's
// singleton DynamoDbEnhancedClient bean. No endpoint override here: unlike the Java
// backend-dynamo module (which supports app.dynamodb.endpoint-override for DynamoDB Local),
// these functions only ever run against real AWS (deployed or via `ampx sandbox`) or against
// DynamoDB Local through DYNAMODB_ENDPOINT_OVERRIDE for local unit/integration tests — see
// lib/repositories/*.test.ts.
const base = new DynamoDBClient(
  process.env.DYNAMODB_ENDPOINT_OVERRIDE ? { endpoint: process.env.DYNAMODB_ENDPOINT_OVERRIDE } : {},
)

export const ddb = DynamoDBDocumentClient.from(base, {
  marshallOptions: { removeUndefinedValues: true },
})

export class VersionConflictError extends Error {
  constructor(message = 'Item was modified concurrently') {
    super(message)
    this.name = 'VersionConflictError'
  }
}
