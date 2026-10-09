import { test, expect } from '@playwright/test'
import { mockLogin, waitForMsw } from '../helpers'

// Direct coverage of the /spotify/callback route itself (see coverage-check.spec.ts) — the
// full connect-button round trip through this same route is covered by ../spotify.spec.ts.
const PLAYLIST_ID = 'play-b9c0d1e2f3a4b5c6'

test.describe('Spotify callback route', () => {
  test.beforeEach(async ({ page }) => {
    await mockLogin(page)
    await waitForMsw(page)
  })

  test('exchanges the code and redirects back to the originating playlist', async ({ page }) => {
    await page.goto(`/spotify/callback?code=mock-code&state=${PLAYLIST_ID}`)
    await expect(page).toHaveURL(new RegExp(`/playlists/${PLAYLIST_ID}`))
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Road Trip Mix')
  })

  test('shows an error and a way back when code/state are missing', async ({ page }) => {
    await page.goto('/spotify/callback')
    await expect(page.getByText('Could not connect your Spotify account')).toBeVisible()
    await page.getByRole('link', { name: 'Back to playlists' }).click()
    await expect(page).toHaveURL('/playlists')
  })
})
