import { getItem, partitionKey, queryIndex, saveItem } from './base'

const TABLE = () => process.env.DYNAMODB_TABLE_GUESS_SUBMISSIONS ?? ''
const ENTITY_TYPE = 'GUESS_SUBMISSION'

/** Marker row meaning a contributor has submitted their guesses for a playlist. */
export interface GuessSubmissionItem {
  pk: string
  sk: string
  id: bigint
  tenantId: number
  playlistId: bigint
  contributorId: bigint
  createdAt: string
  updatedAt: string
  deletedAt?: string
  version?: number
}

export function guessSubmissionKey(tenantId: number, id: bigint) {
  return { pk: partitionKey(tenantId, ENTITY_TYPE), sk: id.toString() }
}

export async function findGuessSubmissionById(tenantId: number, id: bigint): Promise<GuessSubmissionItem | undefined> {
  const { pk, sk } = guessSubmissionKey(tenantId, id)
  return getItem<GuessSubmissionItem>(TABLE(), pk, sk)
}

/** byPlaylist GSI: playlistId (HASH) + contributorId (RANGE) — exact existence check. */
export async function existsGuessSubmission(playlistId: bigint, contributorId: bigint): Promise<boolean> {
  const items = await queryIndex<GuessSubmissionItem>(
    TABLE(),
    'byPlaylist',
    'playlistId = :playlistId AND contributorId = :contributorId',
    { ':playlistId': playlistId, ':contributorId': contributorId },
  )
  return items.length > 0
}

export function saveGuessSubmission(item: GuessSubmissionItem): Promise<GuessSubmissionItem> {
  return saveItem(TABLE(), item)
}
