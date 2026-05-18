import { test, expect } from '@playwright/test'

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
    await page.goto('/')
    // Wait until MSW's service worker has claimed this page
    await page.waitForFunction(() => !!navigator.serviceWorker?.controller)
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
      body: JSON.stringify({ name: 'E2E Contributor', email: 'e2e@example.com' }),
    })
    expect(status).toBe(201)
    expect(body.name).toBe('E2E Contributor')
    expect(body.email).toBe('e2e@example.com')
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
    const { status, body } = await browserFetch(page, '/api/v1/contributors/1')
    expect(status).toBe(200)
    expect(body.name).toBe('Thom Yorke')
  })

  test('returns 404 for unknown id', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/contributors/9999')
    expect(status).toBe(404)
  })

  test('updates a contributor', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/contributors/2', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Updated Name' }),
    })
    expect(status).toBe(200)
    expect(body.name).toBe('Updated Name')
    expect(body.version).toBe(1)
  })

  test('deletes a contributor', async ({ page }) => {
    const del = await browserFetch(page, '/api/v1/contributors/3', { method: 'DELETE' })
    expect(del.status).toBe(204)

    const get = await browserFetch(page, '/api/v1/contributors/3')
    expect(get.status).toBe(404)
  })
})
