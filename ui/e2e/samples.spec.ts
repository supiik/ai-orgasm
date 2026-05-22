import { test, expect } from '@playwright/test'

// MSW seed data (see src/mocks/handlers/samples.ts)
const SEED_NAMES = ['Alpha Widget', 'Beta Gadget', 'Gamma Device']

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

test.describe('samples API (via MSW)', () => {
  test.beforeEach(async ({ page }) => {
    const mswReady = page.waitForResponse(
      async res =>
        res.url().endsWith('/api/health') &&
        res.status() === 200 &&
        (await res.json().catch(() => null))?.success === true,
    )
    await page.goto('/')
    await mswReady
  })

  test('lists seed samples', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/samples')
    expect(status).toBe(200)
    expect(body.page.totalElements).toBe(SEED_NAMES.length)
    expect(body.content.map((s: { name: string }) => s.name)).toEqual(
      expect.arrayContaining(SEED_NAMES),
    )
  })

  test('creates a new sample with all field types', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/samples', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: 'E2E Sample',
        description: 'Created by Playwright',
        email: 'e2e@example.com',
        quantity: 42,
        largeNumber: 9876543210,
        rating: 7.5,
        price: 49.99,
        active: true,
        birthDate: '1985-03-15',
        scheduledAt: '2025-09-01T08:00:00',
        status: 'DRAFT',
        notes: 'E2E test notes',
      }),
    })
    expect(status).toBe(201)
    expect(body.name).toBe('E2E Sample')
    expect(body.id).toBeDefined()
    expect(body.quantity).toBe(42)
    expect(body.active).toBe(true)
    expect(body.status).toBe('DRAFT')
    expect(body.birthDate).toBe('1985-03-15')
  })

  test('returns 400 when name is missing', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/samples', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'DRAFT' }),
    })
    expect(status).toBe(400)
  })

  test('gets a sample by id', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/samples/smpl-0001')
    expect(status).toBe(200)
    expect(body.name).toBe('Alpha Widget')
    expect(body.quantity).toBe(10)
    expect(body.active).toBe(true)
    expect(body.status).toBe('ACTIVE')
  })

  test('returns 404 for unknown id', async ({ page }) => {
    const { status } = await browserFetch(page, '/api/v1/samples/smpl-9999')
    expect(status).toBe(404)
  })

  test('updates a sample', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/samples/smpl-0002', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Updated Gadget', status: 'ACTIVE', active: true }),
    })
    expect(status).toBe(200)
    expect(body.name).toBe('Updated Gadget')
    expect(body.version).toBe(1)
  })

  test('deletes a sample', async ({ page }) => {
    const del = await browserFetch(page, '/api/v1/samples/smpl-0003', { method: 'DELETE' })
    expect(del.status).toBe(204)

    const get = await browserFetch(page, '/api/v1/samples/smpl-0003')
    expect(get.status).toBe(404)
  })

  test('filters samples by name', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/samples?name=alpha')
    expect(status).toBe(200)
    expect(body.page.totalElements).toBe(1)
    expect(body.content[0].name).toBe('Alpha Widget')
  })

  test('filter by name returns empty when no match', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/samples?name=zzznomatch')
    expect(status).toBe(200)
    expect(body.page.totalElements).toBe(0)
    expect(body.content).toHaveLength(0)
  })

  test('navigates to sample detail on row click', async ({ page }) => {
    await page.goto('/samples')
    await expect(page.getByText('Alpha Widget')).toBeVisible()
    await page.locator('table tbody tr').filter({ hasText: 'Alpha Widget' }).click()
    await expect(page).toHaveURL(/\/samples\/smpl-/)
    await expect(page.getByRole('heading', { name: 'Sample detail' })).toBeVisible()
  })
})
