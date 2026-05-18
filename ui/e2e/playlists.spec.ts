import { test, expect } from '@playwright/test'

// MSW seed data (see src/mocks/handlers/playlists.ts)
const SEED_NAMES = ['Chill Vibes', 'Workout Hits', 'Late Night']

// Helper: run a fetch inside the browser (where MSW intercepts it)
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

test.describe('playlists API (via MSW)', () => {
  test.beforeEach(async ({ page }) => {
    // Set up response listener BEFORE goto so we don't miss the health request
    const mswReady = page.waitForResponse(
      async res =>
        res.url().endsWith('/api/health') &&
        res.status() === 200 &&
        (await res.json().catch(() => null))?.success === true,
    )
    await page.goto('/')
    await mswReady
  })

  test('lists seed playlists', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/playlists')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(SEED_NAMES.length)
    expect(body.content.map((p: { name: string }) => p.name)).toEqual(
      expect.arrayContaining(SEED_NAMES),
    )
  })

  test('creates a new playlist', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/playlists', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'E2E Playlist', description: 'Created by Playwright' }),
    })
    expect(status).toBe(201)
    expect(body.name).toBe('E2E Playlist')
    expect(body.id).toBeDefined()
  })

  test('returns 400 when name is missing', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/playlists', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ description: 'no name' }),
    })
    expect(status).toBe(400)
  })

  test('gets a playlist by id', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/playlists/play_0001')
    expect(status).toBe(200)
    expect(body.name).toBe('Chill Vibes')
  })

  test('returns 404 for unknown id', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/playlists/play_9999')
    expect(status).toBe(404)
  })

  test('updates a playlist', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/playlists/play_0002', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Updated Name' }),
    })
    expect(status).toBe(200)
    expect(body.name).toBe('Updated Name')
    expect(body.version).toBe(1)
  })

  test('deletes a playlist', async ({ page }) => {
    const del = await browserFetch(page, '/api/v1/playlists/play_0003', { method: 'DELETE' })
    expect(del.status).toBe(204)

    const get = await browserFetch(page, '/api/v1/playlists/play_0003')
    expect(get.status).toBe(404)
  })

  test('filters playlists by name', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/playlists?name=chill')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(1)
    expect(body.content[0].name).toBe('Chill Vibes')
  })

  test('filter by name returns empty when no match', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/playlists?name=zzznomatch')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(0)
    expect(body.content).toHaveLength(0)
  })
})
