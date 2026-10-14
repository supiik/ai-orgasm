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
// cap tests need an OPEN playlist nobody else has nominated on yet. The name must be unique per
// call (not just per test): playlist names are now globally unique, and MSW's mock DB can
// outlive a single test if the Service Worker isn't torn down between them (see CLAUDE.md's
// "Nomination cap per contributor" mock/e2e note), so two tests reusing the same literal name
// would otherwise race against each other's leftover row. `maxNominationsPerContributor` must be
// set before opening — name/description/maxNominationsPerContributor are only editable while a
// playlist is still NEW (see "Playlist editing restricted to NEW" in CLAUDE.md).
async function createOpenPlaylist(page: import('@playwright/test').Page, maxNominationsPerContributor?: number): Promise<string> {
  const { body: created } = await browserFetch(page, '/api/v1/playlists', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: `E2E Nomination Cap Playlist ${crypto.randomUUID()}` }),
  })
  if (maxNominationsPerContributor !== undefined) {
    await browserFetch(page, `/api/v1/playlists/${created.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: created.name, maxNominationsPerContributor }),
    })
  }
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
    const playlistId = await createOpenPlaylist(page, 2)

    const songA = await createSong(page, 'E2E Song C')
    const songB = await createSong(page, 'E2E Song D')
    const songC = await createSong(page, 'E2E Song E')

    expect((await nominate(page, playlistId, songA, CONTRIBUTOR_ID)).status).toBe(201)
    expect((await nominate(page, playlistId, songB, CONTRIBUTOR_ID)).status).toBe(201)

    const third = await nominate(page, playlistId, songC, CONTRIBUTOR_ID)
    expect(third.status).toBe(409)
    expect(third.body.detail).toMatch(/maximum number of nominations/)
  })

  test('hides the nominate button and shows a message once the logged-in contributor is at the cap', async ({ page }) => {
    // "Workout Hits" (play-2d3e4f5a6b7c8d90) — seeded OPEN, future deadline, default cap of 1.
    // The logged-in mock user (Thom Yorke) starts with zero active nominations on it.
    await page.goto('/playlists/play-2d3e4f5a6b7c8d90')
    const nominateButton = page.getByRole('button', { name: 'Nominate song' })
    const capMessage = page.getByText("You've reached your nomination limit for this playlist")

    await expect(nominateButton).toBeVisible()
    await expect(capMessage).not.toBeVisible()

    await nominateButton.click()
    const dialog = page.getByRole('dialog')
    await dialog.locator('#nom-artist').fill('E2E Artist')
    await dialog.locator('#nom-name').fill('E2E Song F')
    await dialog.getByRole('button', { name: 'Nominate', exact: true }).click()
    await expect(dialog).toBeHidden()

    await expect(nominateButton).not.toBeVisible()
    await expect(capMessage).toBeVisible()
  })
})
