import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

// MSW seed data (see src/mocks/handlers/playlists.ts)
const SEED_NAMES = ['Chill Vibes', 'Workout Hits', 'Late Night', 'Road Trip Mix', 'Summer Classics']

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
    await mockLogin(page)
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
    const { status, body } = await browserFetch(page, '/api/v1/playlists/play-a1b2c3d4e5f60718')
    expect(status).toBe(200)
    expect(body.name).toBe('Chill Vibes')
  })

  test('returns 404 for unknown id', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/playlists/play-ffffffffffffffff')
    expect(status).toBe(404)
  })

  test('updates a playlist', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/playlists/play-2d3e4f5a6b7c8d90', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Updated Name' }),
    })
    expect(status).toBe(200)
    expect(body.name).toBe('Updated Name')
    expect(body.version).toBe(1)
  })

  test('deletes a playlist', async ({ page }) => {
    const del = await browserFetch(page, '/api/v1/playlists/play-e5f6a7b8c9d0e1f2', { method: 'DELETE' })
    expect(del.status).toBe(204)

    const get = await browserFetch(page, '/api/v1/playlists/play-e5f6a7b8c9d0e1f2')
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

  test('navigates to playlist detail on row click', async ({ page }) => {
    await page.goto('/playlists')
    await expect(page.getByText('Chill Vibes')).toBeVisible()
    await page.locator('table tbody tr').filter({ hasText: 'Chill Vibes' }).click()
    await expect(page).toHaveURL(/\/playlists\/play-/)
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Chill Vibes')
  })
})
