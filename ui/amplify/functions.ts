import { HttpMethod } from 'aws-cdk-lib/aws-lambda'
import type { Tables } from './tables'

export interface FnSpec {
  /** Also the AWS::Lambda::FunctionName suffix, e.g. orgasm-<name>-<env>. */
  name: string
  handlerClass: string
  description: string
  tables: (keyof Tables)[]
  methods: HttpMethod[]
  /** Empty for the two fully public endpoints (ListOrganizations, RegisterContributor's create-only body). */
  corsHeaders: string[]
}

/**
 * One entry per Lambda handler, replacing the 34 hand-written `AWS::Serverless::Function`
 * blocks in the former `lambda/template.yaml` (SAM) with data — a loop in `backend.ts`
 * constructs each function, its per-table IAM grants, and its Function URL from this array.
 * `tables` mirrors each SAM function's `Policies: DynamoDBCrudPolicy` list exactly.
 */
export const fnSpecs: FnSpec[] = [
  { name: 'hello', handlerClass: 'HelloHandler', description: 'Health-check / hello endpoint',
    tables: ['playlists'], methods: [HttpMethod.GET], corsHeaders: [] },

  { name: 'create-playlist', handlerClass: 'CreatePlaylistHandler', description: 'Create a playlist',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'get-playlist', handlerClass: 'GetPlaylistHandler', description: 'Get a playlist by id',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-playlists', handlerClass: 'ListPlaylistsHandler', description: 'List playlists',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'update-playlist', handlerClass: 'UpdatePlaylistHandler', description: 'Update a playlist',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'delete-playlist', handlerClass: 'DeletePlaylistHandler', description: 'Delete (soft) a playlist',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.DELETE], corsHeaders: ['authorization'] },

  { name: 'create-song', handlerClass: 'CreateSongHandler', description: 'Create a song',
    tables: ['songs', 'contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'get-song', handlerClass: 'GetSongHandler', description: 'Get a song by id',
    tables: ['songs', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-songs', handlerClass: 'ListSongsHandler', description: 'List songs',
    tables: ['songs', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'update-song', handlerClass: 'UpdateSongHandler', description: 'Update a song',
    tables: ['songs', 'contributors'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'delete-song', handlerClass: 'DeleteSongHandler', description: 'Delete (soft) a song',
    tables: ['songs', 'contributors'], methods: [HttpMethod.DELETE], corsHeaders: ['authorization'] },

  { name: 'create-contributor', handlerClass: 'CreateContributorHandler', description: 'Create a contributor',
    tables: ['contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'get-contributor', handlerClass: 'GetContributorHandler', description: 'Get a contributor by id',
    tables: ['contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-contributors', handlerClass: 'ListContributorsHandler', description: 'List contributors',
    tables: ['contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'update-contributor', handlerClass: 'UpdateContributorHandler', description: 'Update a contributor',
    tables: ['contributors'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'delete-contributor', handlerClass: 'DeleteContributorHandler', description: 'Delete (soft) a contributor',
    tables: ['contributors'], methods: [HttpMethod.DELETE], corsHeaders: ['authorization'] },

  { name: 'list-organizations', handlerClass: 'ListOrganizationsHandler', description: 'List organizations (public, no auth)',
    tables: ['organizations'], methods: [HttpMethod.GET], corsHeaders: [] },
  { name: 'register-contributor', handlerClass: 'RegisterContributorHandler', description: 'Register a contributor under an organization (public, no auth)',
    tables: ['organizations', 'contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type'] },

  { name: 'open-playlist', handlerClass: 'OpenPlaylistHandler', description: 'Open a playlist for nominations',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'list-playlists-by-contributor', handlerClass: 'ListPlaylistsByContributorHandler', description: 'List playlists led by a contributor',
    tables: ['playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'nominate-song', handlerClass: 'NominateSongHandler', description: 'Nominate a song for a playlist',
    tables: ['playlists', 'songs', 'contributors', 'nominations'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'list-nominations', handlerClass: 'ListNominationsHandler', description: 'List nominations for a playlist',
    tables: ['nominations', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-song-nominations', handlerClass: 'ListSongNominationsHandler', description: 'List nominations for a song, across playlists',
    tables: ['nominations', 'playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'approve-nomination', handlerClass: 'ApproveNominationHandler', description: 'Approve a pending nomination',
    tables: ['nominations', 'playlists', 'contributors'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'decline-nomination', handlerClass: 'DeclineNominationHandler', description: 'Decline a pending nomination',
    tables: ['nominations', 'playlists', 'contributors'], methods: [HttpMethod.PUT], corsHeaders: ['content-type', 'authorization'] },
  { name: 'start-guessing', handlerClass: 'StartGuessingHandler', description: 'Close nominations and start the guessing phase',
    tables: ['playlists', 'nominations', 'contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'submit-guesses', handlerClass: 'SubmitGuessesHandler', description: "Submit (replace) a contributor's guesses for a playlist",
    tables: ['playlists', 'contributors', 'nominations', 'guesses', 'guessSubmissions'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'list-guesses', handlerClass: 'ListGuessesHandler', description: 'List guesses for a playlist',
    tables: ['guesses', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'list-rankings', handlerClass: 'ListRankingsHandler', description: 'List rankings across all playlists for the tenant',
    tables: ['playlistRankings', 'playlists', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'submit-ratings', handlerClass: 'SubmitRatingsHandler', description: "Submit (replace) a contributor's ratings for a playlist",
    tables: ['playlists', 'contributors', 'nominations', 'songRatings'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'list-song-ratings', handlerClass: 'ListSongRatingsHandler', description: 'List song ratings for a playlist',
    tables: ['songRatings', 'contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
  { name: 'publish-playlist', handlerClass: 'PublishPlaylistHandler', description: 'Publish a playlist and compute guesser rankings',
    tables: ['playlists', 'nominations', 'guesses', 'playlistRankings', 'contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },

  { name: 'link-contributor', handlerClass: 'LinkContributorHandler', description: 'Link an authenticated Cognito identity to a Contributor (post sign-up)',
    tables: ['organizations', 'contributors'], methods: [HttpMethod.POST], corsHeaders: ['content-type', 'authorization'] },
  { name: 'get-current-contributor', handlerClass: 'GetCurrentContributorHandler', description: 'Get the Contributor linked to the calling Cognito identity (/me equivalent)',
    tables: ['contributors'], methods: [HttpMethod.GET], corsHeaders: ['authorization'] },
]
