import { DeleteCommand } from '@aws-sdk/lib-dynamodb'
import { ddb } from '../dynamodb'
import { partitionKey, putItemNoVersion, queryIndex } from './base'

const TABLE = () => process.env.DYNAMODB_TABLE_GUESSES ?? ''
const ENTITY_TYPE = 'GUESS'

/** Hard-deleted only (no deletedAt) and never updated in place (no version) — matches its JPA source. */
export interface GuessItem {
  pk: string
  sk: string
  id: bigint
  tenantId: number
  playlistId: bigint
  nominationId: bigint
  guesserId: bigint
  guessedContributorId: bigint
  createdAt: string
}

export function guessKey(tenantId: number, id: bigint) {
  return { pk: partitionKey(tenantId, ENTITY_TYPE), sk: id.toString() }
}

/** byPlaylist GSI: playlistId (HASH) + id (RANGE). */
export function findGuessesByPlaylistId(playlistId: bigint): Promise<GuessItem[]> {
  return queryIndex<GuessItem>(TABLE(), 'byPlaylist', 'playlistId = :playlistId', { ':playlistId': playlistId })
}

export function saveGuess(item: GuessItem): Promise<GuessItem> {
  return putItemNoVersion(TABLE(), item)
}

export async function deleteGuess(tenantId: number, id: bigint): Promise<void> {
  const { pk, sk } = guessKey(tenantId, id)
  await ddb.send(new DeleteCommand({ TableName: TABLE(), Key: { pk, sk } }))
}

export async function deleteGuessesByPlaylistAndGuesser(playlistId: bigint, guesserId: bigint): Promise<void> {
  const items = (await findGuessesByPlaylistId(playlistId)).filter((g) => g.guesserId === guesserId)
  for (const item of items) {
    await deleteGuess(item.tenantId, item.id)
  }
}
