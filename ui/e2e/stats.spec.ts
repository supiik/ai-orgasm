import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

function browserFetch(page: import('@playwright/test').Page, input: string) {
  return page.evaluate(
    ({ input }) =>
      fetch(input).then(async (r) => ({
        status: r.status,
        body: r.status === 204 ? null : await r.json().catch(() => null),
      })),
    { input },
  )
}

test.describe('stats (via MSW)', () => {
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

  test('rankings API returns data for published playlists', async ({ page }) => {
    const { status, body } = await browserFetch(page, '/api/v1/rankings')
    expect(status).toBe(200)
    expect(Array.isArray(body)).toBe(true)
    expect(body.length).toBeGreaterThan(0)
    for (const entry of body) {
      expect(entry).toHaveProperty('playlistName')
      expect(entry).toHaveProperty('contributorName')
      expect(entry).toHaveProperty('rankPosition')
      expect(entry).toHaveProperty('correctGuesses')
      expect(entry).toHaveProperty('totalGuesses')
    }
  })

  test('stats page shows leaderboard', async ({ page }) => {
    await page.getByRole('link', { name: 'Stats' }).click()
    await expect(page).toHaveURL('/stats')
    await expect(page.getByRole('heading', { name: 'Stats' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'All-Time Leaderboard' })).toBeVisible()
    await expect(page.getByText('Summer Classics')).toBeVisible()
  })
})
