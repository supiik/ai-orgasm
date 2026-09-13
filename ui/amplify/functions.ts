import { HttpMethod } from 'aws-cdk-lib/aws-lambda'
import type { Tables } from './tables'

export interface FnSpec {
  /** Matches this function's folder name under functions/, its defineFunction name, and the AWS::Lambda::FunctionName suffix. */
  name: string
  description: string
  /**
   * Every table this function touches. Granted READ access by default — note that each
   * `authenticated-with-contributor` function reads `contributors` implicitly, via `withAuth`'s
   * `findContributorByCognitoSub` lookup, even when its own body never touches that table.
   */
  tables: (keyof Tables)[]
  /** Subset of `tables` this function actually writes to; these get read+write instead. */
  writes?: (keyof Tables)[]
  methods: HttpMethod[]
  /** Empty for the fully public endpoints (hello, ListOrganizations). */
  corsHeaders: string[]
  /**
   * Reachable without a bearer token, so a flood costs us Lambda time before any auth check can
   * reject it. `backend.ts` gives these — and only these — a reserved-concurrency cap; the rest
   * are gated by Cognito. See CLAUDE.md "Known gaps" for what this does and doesn't mitigate.
   */
  publiclyReachable?: boolean
}

/**
 * One entry per Lambda function — `backend.ts` loops this to grant each function's IAM access
 * to exactly the tables it touches and add its Function URL + CORS config. Each function itself
 * is defined natively via `defineFunction` in functions/<name>/resource.ts (see CLAUDE.md
 * "Amplify Gen 2 backend" — this predates and replaced a CDK-escape-hatch, single-Java-image
 * design once the app was rewritten in TypeScript).
 */
export const fnSpecs: FnSpec[] = [
  { name: 'hello', description: 'Health-check / hello endpoint',
    tables: [], methods: [HttpMethod.GET], corsHeaders: [], publiclyReachable: true },

  { name: 'create-playlist', description: 'Create a playlist',
    tables: ['playlists', 'contributors'], writes: ['playlists'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'get-playlist', description: 'Get a playlist by id',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-playlists', description: 'List playlists',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'update-playlist', description: 'Update a playlist',
    tables: ['playlists', 'contributors'], writes: ['playlists'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'delete-playlist', description: 'Delete (soft) a playlist',
    tables: ['playlists', 'contributors'], writes: ['playlists'], methods: [HttpMethod.DELETE], corsHeaders: ['authorization'] },

  { name: 'create-song', description: 'Create a song',
    tables: ['songs', 'contributors'], writes: ['songs'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'get-song', description: 'Get a song by id',
    tables: ['songs', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-songs', description: 'List songs',
    tables: ['songs', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'update-song', description: 'Update a song',
    tables: ['songs', 'contributors'], writes: ['songs'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'delete-song', description: 'Delete (soft) a song',
    tables: ['songs', 'contributors'], writes: ['songs'], methods: [HttpMethod.DELETE], corsHeaders: ['authorization'] },

  { name: 'create-contributor', description: 'Create a contributor',
    tables: ['contributors'], writes: ['contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'get-contributor', description: 'Get a contributor by id',
    tables: ['contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-contributors', description: 'List contributors',
    tables: ['contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'update-contributor', description: 'Update a contributor',
    tables: ['contributors'], writes: ['contributors'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'delete-contributor', description: 'Delete (soft) a contributor',
    tables: ['contributors'], writes: ['contributors'], methods: [HttpMethod.DELETE], corsHeaders: ['authorization'] },

  { name: 'list-organizations', description: 'List organizations (public, no auth)',
    tables: ['organizations'], methods: [HttpMethod.GET], corsHeaders: [], publiclyReachable: true },

  { name: 'open-playlist', description: 'Open a playlist for nominations',
    tables: ['playlists', 'contributors'], writes: ['playlists'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'list-playlists-by-contributor', description: 'List playlists led by a contributor',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'nominate-song', description: 'Nominate a song for a playlist',
    tables: ['playlists', 'songs', 'contributors', 'nominations'], writes: ['nominations'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'list-nominations', description: 'List nominations for a playlist',
    tables: ['nominations', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-song-nominations', description: 'List nominations for a song, across playlists',
    tables: ['nominations', 'playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'approve-nomination', description: 'Approve a pending nomination',
    tables: ['nominations', 'playlists', 'contributors'], writes: ['nominations'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'decline-nomination', description: 'Decline a pending nomination',
    tables: ['nominations', 'playlists', 'contributors'], writes: ['nominations'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'start-guessing', description: 'Close nominations and start the guessing phase',
    tables: ['playlists', 'nominations', 'contributors'], writes: ['playlists', 'nominations'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'submit-guesses', description: "Submit (replace) a contributor's guesses for a playlist",
    tables: ['playlists', 'contributors', 'nominations', 'guesses', 'guessSubmissions'], writes: ['guesses', 'guessSubmissions'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'list-guesses', description: 'List guesses for a playlist',
    tables: ['guesses', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-rankings', description: 'List rankings across all playlists for the tenant',
    tables: ['playlistRankings', 'playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'submit-ratings', description: "Submit (replace) a contributor's ratings for a playlist",
    tables: ['playlists', 'contributors', 'nominations', 'songRatings'], writes: ['songRatings'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'list-song-ratings', description: 'List song ratings for a playlist',
    tables: ['songRatings', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'publish-playlist', description: 'Publish a playlist and compute guesser rankings',
    tables: ['playlists', 'nominations', 'guesses', 'playlistRankings', 'contributors'], writes: ['playlists', 'playlistRankings'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },

  { name: 'link-contributor', description: 'Link an authenticated Cognito identity to a Contributor (post sign-up)',
    tables: ['organizations', 'contributors'], writes: ['contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'get-current-contributor', description: 'Get the Contributor linked to the calling Cognito identity (/me equivalent)',
    tables: ['contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
]
