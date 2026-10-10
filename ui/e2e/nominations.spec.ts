import { test, expect } from '@playwright/test'
import { mockLogin, waitForMsw } from './helpers'

const CONTRIBUTOR_ID = 'cont-8f7e6d5c4b3a2019' // Nigel Godrich

function browserFetch(page: import('@playwright/test').Page, input: string, init?: RequestInit) {
  return page.evaluate(
    ({ input, init }) =>
      fetch(input, init).then(async (r) => ({
        status: r.status,
        body: r.status === 204 ? null : await r.json().catch(() => null),
      })),
    { input, init },
  )
}

async function createSong(page: import('@playwright/test').Page, name: string) {
  const { body } = await browserFetch(page, '/api/v1/songs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ artist: 'E2E Artist', name }),
  })
  return body.id as string
}

// Creates a fresh NEW playlist and opens it (future deadline), independent of seed data —
// cap tests need an OPEN playlist nobody else has nominated on yet.
async function createOpenPlaylist(page: import('@playwright/test').Page): Promise<string> {
  const { body: created } = await browserFetch(page, '/api/v1/playlists', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: 'E2E Nomination Cap Playlist' }),
  })
  await browserFetch(page, `/api/v1/playlists/${created.id}/open`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contributorId: CONTRIBUTOR_ID, deadline: new Date(Date.now() + 86400000).toISOString() }),
  })
  return created.id as string
}

function nominate(page: import('@playwright/test').Page, playlistId: string, songId: string, contributorId: string) {
  return browserFetch(page, `/api/v1/playlists/${playlistId}/nominations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contributorId, songId }),
  })
}

test.describe('nomination cap (via MSW)', () => {
  test.beforeEach(async ({ page }) => {
    await mockLogin(page)
    await waitForMsw(page)
  })

  test('enforces the default cap of one nomination per contributor', async ({ page }) => {
    const playlistId = await createOpenPlaylist(page)
    const songA = await createSong(page, 'E2E Song A')
    const songB = await createSong(page, 'E2E Song B')

    const first = await nominate(page, playlistId, songA, CONTRIBUTOR_ID)
    expect(first.status).toBe(201)
    expect(first.body.maxNominationsPerContributor ?? 1).toBeDefined()

    const second = await nominate(page, playlistId, songB, CONTRIBUTOR_ID)
    expect(second.status).toBe(409)
    expect(second.body.detail).toMatch(/maximum number of nominations/)
  })

  test('allows nominating up to a raised maxNominationsPerContributor', async ({ page }) => {
    const playlistId = await createOpenPlaylist(page)

    const raised = await browserFetch(page, `/api/v1/playlists/${playlistId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'E2E Nomination Cap Playlist', maxNominationsPerContributor: 2 }),
    })
    expect(raised.body.maxNominationsPerContributor).toBe(2)

    const songA = await createSong(page, 'E2E Song C')
    const songB = await createSong(page, 'E2E Song D')
    const songC = await createSong(page, 'E2E Song E')

    expect((await nominate(page, playlistId, songA, CONTRIBUTOR_ID)).status).toBe(201)
    expect((await nominate(page, playlistId, songB, CONTRIBUTOR_ID)).status).toBe(201)

    const third = await nominate(page, playlistId, songC, CONTRIBUTOR_ID)
    expect(third.status).toBe(409)
    expect(third.body.detail).toMatch(/maximum number of nominations/)
  })
})
