import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

// MSW seed data (see src/mocks/handlers/songs.ts)
const SEED = [
  { id: 'song-0af3b7c2d1e8f905', artist: 'Radiohead', name: 'Creep' },
  { id: 'song-9b2c5e3a7f1d4680', artist: 'Nirvana', name: 'Smells Like Teen Spirit' },
  { id: 'song-c4d7a8e2f3b16509', artist: 'Oasis', name: 'Wonderwall' },
]

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

test.describe('songs API (via MSW)', () => {
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

  test('lists seed songs', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/songs')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(SEED.length)
    expect(body.content.map((s: { name: string }) => s.name)).toEqual(
      expect.arrayContaining(SEED.map((s) => s.name)),
    )
  })

  test('creates a new song', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/songs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ artist: 'The Beatles', name: 'Hey Jude', album: 'Single', releaseYear: 1968 }),
    })
    expect(status).toBe(201)
    expect(body.artist).toBe('The Beatles')
    expect(body.name).toBe('Hey Jude')
    expect(body.album).toBe('Single')
    expect(body.releaseYear).toBe(1968)
    expect(body.id).toBeDefined()
  })

  test('returns 400 when artist is missing', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/songs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Some Track' }),
    })
    expect(status).toBe(400)
  })

  test('returns 400 when name is missing', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/songs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ artist: 'Some Artist' }),
    })
    expect(status).toBe(400)
  })

  test('gets a song by id', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/songs/song-0af3b7c2d1e8f905')
    expect(status).toBe(200)
    expect(body.artist).toBe('Radiohead')
    expect(body.name).toBe('Creep')
    expect(body.album).toBe('Pablo Honey')
    expect(body.releaseYear).toBe(1993)
  })

  test('returns 404 for unknown id', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/songs/song-ffffffffffffffff')
    expect(status).toBe(404)
  })

  test('updates a song', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/songs/song-9b2c5e3a7f1d4680', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ artist: 'Nirvana', name: 'Come as You Are', album: 'Nevermind', releaseYear: 1992 }),
    })
    expect(status).toBe(200)
    expect(body.name).toBe('Come as You Are')
    expect(body.releaseYear).toBe(1992)
    expect(body.version).toBe(1)
  })

  test('returns 400 on update when artist is blank', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/songs/song-0af3b7c2d1e8f905', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ artist: '', name: 'Creep' }),
    })
    expect(status).toBe(400)
  })

  test('deletes a song', async ({ page }) => {
    const del = await browserFetch(page, '/api/v1/songs/song-c4d7a8e2f3b16509', { method: 'DELETE' })
    expect(del.status).toBe(204)

    const get = await browserFetch(page, '/api/v1/songs/song-c4d7a8e2f3b16509')
    expect(get.status).toBe(404)
  })

  test('filters songs by name', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/songs?name=creep')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(1)
    expect(body.content[0].name).toBe('Creep')
  })

  test('filter by name returns empty when no match', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/songs?name=zzznomatch')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(0)
    expect(body.content).toHaveLength(0)
  })

  test('navigates to song detail on row click', async ({ page }) => {
    await page.goto('/songs')
    await expect(page.getByText('Radiohead')).toBeVisible()
    await page.locator('table tbody tr').filter({ hasText: 'Radiohead' }).click()
    await expect(page).toHaveURL(/\/songs\/song-/)
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Creep')
  })
})
