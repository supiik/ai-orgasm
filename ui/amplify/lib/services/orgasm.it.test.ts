import { beforeAll, describe, expect, it } from 'vitest'

/**
 * Exercises the full Orgasm workflow end-to-end against real DynamoDB Local, across all 8
 * tables: open -> nominate -> approve -> start-guessing -> submit-guesses -> publish -> rankings
 * -> submit-ratings -> ratings. Ports OrgasmServiceIT.java's one broad happy-path test — the
 * interesting bugs in a port like this live in the state transitions between steps, individual
 * validation rules are covered by orgasm.test.ts's mocked unit tests instead.
 *
 * Requires DynamoDB Local running and the 8 tables pre-created (matching the env var names set
 * below) — see CLAUDE.md "Lambda local testing" for the `docker run amazon/dynamodb-local` setup;
 * this test does not start its own container (Vitest has no Testcontainers-equivalent wired up
 * here), so it's opt-in via RUN_DYNAMO_IT rather than part of the default `npm run test:unit`.
 */
const RUN_IT = process.env.RUN_DYNAMO_IT === 'true'
const describeIt = RUN_IT ? describe : describe.skip

describeIt('Orgasm full workflow (DynamoDB Local)', () => {
  let createContributor: typeof import('../services/contributor').createContributor
  let createSong: typeof import('../services/song').createSong
  let createPlaylist: typeof import('../services/playlist').createPlaylist
  let orgasm: typeof import('./orgasm')

  const TENANT_ID = 1

  beforeAll(async () => {
    process.env.DYNAMODB_ENDPOINT_OVERRIDE = process.env.DYNAMODB_ENDPOINT_OVERRIDE ?? 'http://localhost:18000'
    process.env.AWS_ACCESS_KEY_ID = process.env.AWS_ACCESS_KEY_ID ?? 'local'
    process.env.AWS_SECRET_ACCESS_KEY = process.env.AWS_SECRET_ACCESS_KEY ?? 'local'
    process.env.AWS_REGION = process.env.AWS_REGION ?? 'us-east-1'
    process.env.DYNAMODB_TABLE_PLAYLISTS = 'orgasm-it-playlists'
    process.env.DYNAMODB_TABLE_SONGS = 'orgasm-it-songs'
    process.env.DYNAMODB_TABLE_CONTRIBUTORS = 'orgasm-it-contributors'
    process.env.DYNAMODB_TABLE_NOMINATIONS = 'orgasm-it-nominations'
    process.env.DYNAMODB_TABLE_GUESSES = 'orgasm-it-guesses'
    process.env.DYNAMODB_TABLE_GUESS_SUBMISSIONS = 'orgasm-it-guess-submissions'
    process.env.DYNAMODB_TABLE_SONG_RATINGS = 'orgasm-it-song-ratings'
    process.env.DYNAMODB_TABLE_PLAYLIST_RANKINGS = 'orgasm-it-playlist-rankings'

    // Dynamic imports, deliberately — dynamodb.ts constructs its DynamoDBDocumentClient singleton
    // (reading DYNAMODB_ENDPOINT_OVERRIDE) at module top-level, so it must not load until the env
    // vars above are set. A static import would be hoisted ahead of this function body.
    ;({ createContributor } = await import('../services/contributor'))
    ;({ createSong } = await import('../services/song'))
    ;({ createPlaylist } = await import('../services/playlist'))
    orgasm = await import('./orgasm')
  })

  it('runs open through ratings', async () => {
    const lead = await createContributor(TENANT_ID, { name: 'Lead' })
    const guesser1 = await createContributor(TENANT_ID, { name: 'Guesser1' })
    const guesser2 = await createContributor(TENANT_ID, { name: 'Guesser2' })

    const song1 = await createSong(TENANT_ID, { artist: 'Artist A', name: 'Song A' })
    const song2 = await createSong(TENANT_ID, { artist: 'Artist B', name: 'Song B' })

    const playlist = await createPlaylist(TENANT_ID, { name: 'Test Mix', ratingType: 'BEST_SONG' })

    const opened = await orgasm.openPlaylist(TENANT_ID, playlist.id, {
      contributorId: lead.id,
      deadline: new Date(Date.now() + 3_600_000).toISOString(),
    })
    expect(opened.status).toBe('OPEN')
    expect(opened.leadContributorId).toBe(lead.id)
    expect(opened.leadContributorName).toBe('Lead')

    const nomination1 = await orgasm.nominateSong(TENANT_ID, playlist.id, { contributorId: guesser1.id, songId: song1.id })
    const nomination2 = await orgasm.nominateSong(TENANT_ID, playlist.id, { contributorId: guesser2.id, songId: song2.id })
    expect(nomination1.status).toBe('PENDING')

    expect((await orgasm.findNominations(TENANT_ID, playlist.id, undefined)).content).toHaveLength(2)
    expect(await orgasm.findNominationsBySong(TENANT_ID, song1.id)).toHaveLength(1)

    await orgasm.approveNomination(TENANT_ID, nomination1.id, { reviewerId: lead.id })
    await orgasm.approveNomination(TENANT_ID, nomination2.id, { reviewerId: lead.id })

    const guessingStarted = await orgasm.startGuessing(TENANT_ID, playlist.id, { contributorId: lead.id })
    expect(guessingStarted.status).toBe('GUESSING')
    expect(guessingStarted.guessingDeadline).toBeDefined()

    // guesser1 gets both right
    await orgasm.submitGuesses(TENANT_ID, playlist.id, {
      contributorId: guesser1.id,
      guesses: [
        { nominationId: nomination1.id, guessedContributorId: guesser1.id },
        { nominationId: nomination2.id, guessedContributorId: guesser2.id },
      ],
    })

    // guesser2 gets both wrong
    await orgasm.submitGuesses(TENANT_ID, playlist.id, {
      contributorId: guesser2.id,
      guesses: [
        { nominationId: nomination1.id, guessedContributorId: guesser2.id },
        { nominationId: nomination2.id, guessedContributorId: guesser1.id },
      ],
    })

    expect(await orgasm.getGuesses(playlist.id)).toHaveLength(4)

    const published = await orgasm.publishPlaylist(TENANT_ID, playlist.id, { contributorId: lead.id })
    expect(published.status).toBe('PUBLISHED')

    const rankings = await orgasm.getRankings(TENANT_ID)
    expect(rankings).toHaveLength(2)
    const byContributor = new Map(rankings.map((r) => [r.contributorId, r]))
    expect(byContributor.get(guesser1.id)?.correctGuesses).toBe(2)
    expect(byContributor.get(guesser1.id)?.rankPosition).toBe(1)
    expect(byContributor.get(guesser2.id)?.correctGuesses).toBe(0)
    expect(byContributor.get(guesser2.id)?.rankPosition).toBe(2)

    // guesser1 rates guesser2's nomination (not their own) with the single BEST_SONG point
    await orgasm.submitRatings(TENANT_ID, playlist.id, {
      contributorId: guesser1.id,
      ratings: [{ nominationId: nomination2.id, points: 1 }],
    })

    const songRatings = await orgasm.getSongRatings(TENANT_ID, playlist.id)
    expect(songRatings).toHaveLength(1)
    expect(songRatings[0].nominationId).toBe(nomination2.id)
    expect(songRatings[0].contributorId).toBe(guesser1.id)
    expect(songRatings[0].points).toBe(1)

    expect((await orgasm.findPlaylistsByContributor(TENANT_ID, lead.id, undefined)).content).toHaveLength(1)
  })
})
