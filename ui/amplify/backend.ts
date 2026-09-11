import { defineBackend } from '@aws-amplify/backend'
import { FunctionUrlAuthType, type IFunction, type Function as CdkFunction } from 'aws-cdk-lib/aws-lambda'
import { auth } from './auth/resource'
import { fnSpecs } from './functions'
import { createTables } from './tables'

import { helloFn } from './functions/hello/resource'
import { createPlaylistFn } from './functions/create-playlist/resource'
import { getPlaylistFn } from './functions/get-playlist/resource'
import { listPlaylistsFn } from './functions/list-playlists/resource'
import { updatePlaylistFn } from './functions/update-playlist/resource'
import { deletePlaylistFn } from './functions/delete-playlist/resource'
import { createSongFn } from './functions/create-song/resource'
import { getSongFn } from './functions/get-song/resource'
import { listSongsFn } from './functions/list-songs/resource'
import { updateSongFn } from './functions/update-song/resource'
import { deleteSongFn } from './functions/delete-song/resource'
import { createContributorFn } from './functions/create-contributor/resource'
import { getContributorFn } from './functions/get-contributor/resource'
import { listContributorsFn } from './functions/list-contributors/resource'
import { updateContributorFn } from './functions/update-contributor/resource'
import { deleteContributorFn } from './functions/delete-contributor/resource'
import { listOrganizationsFn } from './functions/list-organizations/resource'
import { registerContributorFn } from './functions/register-contributor/resource'
import { openPlaylistFn } from './functions/open-playlist/resource'
import { listPlaylistsByContributorFn } from './functions/list-playlists-by-contributor/resource'
import { nominateSongFn } from './functions/nominate-song/resource'
import { listNominationsFn } from './functions/list-nominations/resource'
import { listSongNominationsFn } from './functions/list-song-nominations/resource'
import { approveNominationFn } from './functions/approve-nomination/resource'
import { declineNominationFn } from './functions/decline-nomination/resource'
import { startGuessingFn } from './functions/start-guessing/resource'
import { submitGuessesFn } from './functions/submit-guesses/resource'
import { listGuessesFn } from './functions/list-guesses/resource'
import { listRankingsFn } from './functions/list-rankings/resource'
import { submitRatingsFn } from './functions/submit-ratings/resource'
import { listSongRatingsFn } from './functions/list-song-ratings/resource'
import { publishPlaylistFn } from './functions/publish-playlist/resource'
import { linkContributorFn } from './functions/link-contributor/resource'
import { getCurrentContributorFn } from './functions/get-current-contributor/resource'

const backend = defineBackend({
  auth,
  helloFn,
  createPlaylistFn,
  getPlaylistFn,
  listPlaylistsFn,
  updatePlaylistFn,
  deletePlaylistFn,
  createSongFn,
  getSongFn,
  listSongsFn,
  updateSongFn,
  deleteSongFn,
  createContributorFn,
  getContributorFn,
  listContributorsFn,
  updateContributorFn,
  deleteContributorFn,
  listOrganizationsFn,
  registerContributorFn,
  openPlaylistFn,
  listPlaylistsByContributorFn,
  nominateSongFn,
  listNominationsFn,
  listSongNominationsFn,
  approveNominationFn,
  declineNominationFn,
  startGuessingFn,
  submitGuessesFn,
  listGuessesFn,
  listRankingsFn,
  submitRatingsFn,
  listSongRatingsFn,
  publishPlaylistFn,
  linkContributorFn,
  getCurrentContributorFn,
})

// Per-branch env name: `ampx pipeline-deploy --branch <name>` sets AWS_BRANCH in the Amplify
// Hosting build environment; `ampx sandbox` falls back to a fixed local dev name.
const envName = process.env.AWS_BRANCH ?? 'sandbox'

const stack = backend.createStack('LambdaApi')
const tables = createTables(stack, envName)

const userPool = backend.auth.resources.userPool
const userPoolClient = backend.auth.resources.userPoolClient

const sharedEnv: Record<string, string> = {
  DYNAMODB_TABLE_PLAYLISTS: tables.playlists.tableName,
  DYNAMODB_TABLE_SONGS: tables.songs.tableName,
  DYNAMODB_TABLE_CONTRIBUTORS: tables.contributors.tableName,
  DYNAMODB_TABLE_ORGANIZATIONS: tables.organizations.tableName,
  DYNAMODB_TABLE_NOMINATIONS: tables.nominations.tableName,
  DYNAMODB_TABLE_GUESSES: tables.guesses.tableName,
  DYNAMODB_TABLE_GUESS_SUBMISSIONS: tables.guessSubmissions.tableName,
  DYNAMODB_TABLE_SONG_RATINGS: tables.songRatings.tableName,
  DYNAMODB_TABLE_PLAYLIST_RANKINGS: tables.playlistRankings.tableName,
  COGNITO_USER_POOL_ID: userPool.userPoolId,
  COGNITO_CLIENT_ID: userPoolClient.userPoolClientId,
}

// Keyed by fnSpecs' `name` — maps each spec to the actual defineFunction resource created above.
const fnResources: Record<string, { resources: { lambda: IFunction } }> = {
  hello: backend.helloFn,
  'create-playlist': backend.createPlaylistFn,
  'get-playlist': backend.getPlaylistFn,
  'list-playlists': backend.listPlaylistsFn,
  'update-playlist': backend.updatePlaylistFn,
  'delete-playlist': backend.deletePlaylistFn,
  'create-song': backend.createSongFn,
  'get-song': backend.getSongFn,
  'list-songs': backend.listSongsFn,
  'update-song': backend.updateSongFn,
  'delete-song': backend.deleteSongFn,
  'create-contributor': backend.createContributorFn,
  'get-contributor': backend.getContributorFn,
  'list-contributors': backend.listContributorsFn,
  'update-contributor': backend.updateContributorFn,
  'delete-contributor': backend.deleteContributorFn,
  'list-organizations': backend.listOrganizationsFn,
  'register-contributor': backend.registerContributorFn,
  'open-playlist': backend.openPlaylistFn,
  'list-playlists-by-contributor': backend.listPlaylistsByContributorFn,
  'nominate-song': backend.nominateSongFn,
  'list-nominations': backend.listNominationsFn,
  'list-song-nominations': backend.listSongNominationsFn,
  'approve-nomination': backend.approveNominationFn,
  'decline-nomination': backend.declineNominationFn,
  'start-guessing': backend.startGuessingFn,
  'submit-guesses': backend.submitGuessesFn,
  'list-guesses': backend.listGuessesFn,
  'list-rankings': backend.listRankingsFn,
  'submit-ratings': backend.submitRatingsFn,
  'list-song-ratings': backend.listSongRatingsFn,
  'publish-playlist': backend.publishPlaylistFn,
  'link-contributor': backend.linkContributorFn,
  'get-current-contributor': backend.getCurrentContributorFn,
}

const functionUrls: Record<string, string> = {}

for (const spec of fnSpecs) {
  const fn = fnResources[spec.name].resources.lambda
  for (const [key, value] of Object.entries(sharedEnv)) {
    ;(fn as unknown as CdkFunction).addEnvironment(key, value)
  }

  for (const tableKey of spec.tables) {
    tables[tableKey].grantReadWriteData(fn)
  }

  const url = fn.addFunctionUrl({
    authType: FunctionUrlAuthType.NONE,
    cors: {
      allowedOrigins: ['*'],
      allowedMethods: spec.methods,
      allowedHeaders: spec.corsHeaders.length > 0 ? spec.corsHeaders : undefined,
    },
  })

  functionUrls[spec.name] = url.url
}

backend.addOutput({
  custom: {
    functionUrls,
  },
})
