import { test, expect } from '@playwright/test'
import { mockLogin, waitForMsw } from './helpers'

// MSW seed data (see src/mocks/handlers/contributors.ts)
const SEED_NAMES = ['Thom Yorke', 'Nigel Godrich', 'Jonny Greenwood']

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

test.describe('contributors API (via MSW)', () => {
  test.beforeEach(async ({ page }) => {
    await mockLogin(page)
    await waitForMsw(page)
  })

  test('lists seed contributors', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/contributors')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(SEED_NAMES.length)
    expect(body.content.map((c: { name: string }) => c.name)).toEqual(
      expect.arrayContaining(SEED_NAMES),
    )
  })

  test('creates a new contributor', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/contributors', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'E2E Contributor', email: 'e2e@example.com', avatarUrl: 'https://example.com/e2e.jpg' }),
    })
    expect(status).toBe(201)
    expect(body.name).toBe('E2E Contributor')
    expect(body.email).toBe('e2e@example.com')
    expect(body.avatarUrl).toBe('https://example.com/e2e.jpg')
    expect(body.id).toBeDefined()
  })

  test('returns 400 when name is missing', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/contributors', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'no-name@example.com' }),
    })
    expect(status).toBe(400)
  })

  test('gets a contributor by id', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/contributors/cont-1a2b3c4d5e6f7089')
    expect(status).toBe(200)
    expect(body.name).toBe('Thom Yorke')
  })

  test('returns 404 for unknown id', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/contributors/cont-ffffffffffffffff')
    expect(status).toBe(404)
  })

  test('lists playlists a contributor has nominated into, distinct by playlist', async ({ page }) => {
    // Nigel Godrich (cont-8f7e6d5c4b3a2019) has seeded nominations across 4 distinct playlists.
    const { status, body } = await browserFetch(page, '/api/v1/contributors/cont-8f7e6d5c4b3a2019/contributed-playlists')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(4)
  })

  test('contributed-playlists is empty for a contributor with no nominations', async ({ page }) => {
    const created = await browserFetch(page, '/api/v1/contributors', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Nobody Nominates' }),
    })
    const { status, body } = await browserFetch(page, `/api/v1/contributors/${created.body.id}/contributed-playlists`)
    expect(status).toBe(200)
    expect(body.totalElements).toBe(0)
  })

  test('updates a contributor', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/contributors/cont-8f7e6d5c4b3a2019', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Updated Name', avatarUrl: 'https://example.com/updated.jpg' }),
    })
    expect(status).toBe(200)
    expect(body.name).toBe('Updated Name')
    expect(body.avatarUrl).toBe('https://example.com/updated.jpg')
    expect(body.version).toBe(1)
  })

  test('deletes a contributor', async ({ page }) => {
    const del = await browserFetch(page, '/api/v1/contributors/cont-0c1d2e3f4a5b6c7d', { method: 'DELETE' })
    expect(del.status).toBe(204)

    const get = await browserFetch(page, '/api/v1/contributors/cont-0c1d2e3f4a5b6c7d')
    expect(get.status).toBe(404)
  })

  test('filters contributors by name', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/contributors?name=thom')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(1)
    expect(body.content[0].name).toBe('Thom Yorke')
  })

  test('filter by name returns empty when no match', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/contributors?name=zzznomatch')
    expect(status).toBe(200)
    expect(body.totalElements).toBe(0)
    expect(body.content).toHaveLength(0)
  })

  test('navigates to contributor detail on row click', async ({ page }) => {
    await page.goto('/contributors')
    await expect(page.getByText('Thom Yorke')).toBeVisible()
    await page.locator('table tbody tr').filter({ hasText: 'Thom Yorke' }).click()
    await expect(page).toHaveURL(/\/contributors\/cont-/)
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Thom Yorke')
  })

  test('shows both led and contributed playlists on the detail page', async ({ page }) => {
    // Nigel Godrich (cont-8f7e6d5c4b3a2019) leads "Summer Classics" and has nominated songs
    // into "Workout Hits", "Late Night" and "Road Trip Mix" too — see src/mocks/handlers/db.ts.
    await page.goto('/contributors/cont-8f7e6d5c4b3a2019')
    await expect(page.getByRole('heading', { name: 'Led playlists' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Contributed playlists' })).toBeVisible()

    const ledSection = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Led playlists' }) })
    await expect(ledSection.getByText('Summer Classics')).toBeVisible()

    const contributedSection = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Contributed playlists' }) })
    await expect(contributedSection.getByText('Workout Hits')).toBeVisible()
    await expect(contributedSection.getByText('Late Night')).toBeVisible()
    await expect(contributedSection.getByText('Road Trip Mix')).toBeVisible()
  })
})
