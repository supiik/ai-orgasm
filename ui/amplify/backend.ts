import { defineBackend } from '@aws-amplify/backend'
import { Duration } from 'aws-cdk-lib'
import { Repository } from 'aws-cdk-lib/aws-ecr'
import { Architecture, Code, Function as LambdaFunction, FunctionUrlAuthType, Handler, Runtime } from 'aws-cdk-lib/aws-lambda'
import { auth } from './auth/resource'
import { fnSpecs } from './functions'
import { createTables } from './tables'

const backend = defineBackend({ auth })

// Per-branch env name: `ampx pipeline-deploy --branch <name>` sets AWS_BRANCH in the Amplify
// Hosting build environment; `ampx sandbox` falls back to a fixed local dev name.
const envName = process.env.AWS_BRANCH ?? 'sandbox'

// Set by the `backend.build.commands` step in amplify.yml, right after it builds and pushes
// the Lambda image — see CLAUDE.md "Cognito auth for the Lambda API" / Lambda deployment.
const repoName = process.env.LAMBDA_ECR_REPO ?? `orgasm-lambda-${envName}`
const imageTag = process.env.LAMBDA_IMAGE_TAG ?? 'latest'

const stack = backend.createStack('LambdaApi')

const tables = createTables(stack, envName)
const repo = Repository.fromRepositoryName(stack, 'LambdaImageRepo', repoName)

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
  COGNITO_REGION: stack.region,
  COGNITO_CLIENT_ID: userPoolClient.userPoolClientId,
}

const functionUrls: Record<string, string> = {}

for (const spec of fnSpecs) {
  const fn = new LambdaFunction(stack, spec.name, {
    functionName: `orgasm-${spec.name}-${envName}`,
    description: spec.description,
    code: Code.fromEcrImage(repo, { tagOrDigest: imageTag, cmd: [`com.orgasm.lambda.${spec.handlerClass}::handleRequest`] }),
    handler: Handler.FROM_IMAGE,
    runtime: Runtime.FROM_IMAGE,
    architecture: Architecture.ARM_64,
    memorySize: 512,
    timeout: Duration.seconds(30),
    environment: sharedEnv,
  })

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
