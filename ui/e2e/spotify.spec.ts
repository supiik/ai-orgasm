import { test, expect } from '@playwright/test'
import { mockLogin, waitForMsw } from './helpers'

// Road Trip Mix — GUESSING, led by Thom Yorke (mockLogin's contributor), 3 APPROVED nominations
// (see src/mocks/handlers/db.ts). The mocked authorizeUrl points straight back at this app's own
// /spotify/callback route with a fake code/state (src/mocks/handlers/spotify.ts), so clicking
// "Connect Spotify" exercises the full round trip without ever leaving the page.
const PLAYLIST_ID = 'play-b9c0d1e2f3a4b5c6'

test.describe('Spotify playlist export', () => {
  test.beforeEach(async ({ page }) => {
    await mockLogin(page)
    await waitForMsw(page)
  })

  test('connects Spotify and creates a playlist from the approved nominations', async ({ page }) => {
    await page.goto(`/playlists/${PLAYLIST_ID}`)
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Road Trip Mix')

    await page.getByRole('button', { name: 'Create Spotify playlist' }).click()
    await expect(page.getByText('Connect your Spotify account')).toBeVisible()

    await page.getByRole('button', { name: 'Connect Spotify' }).click()
    // SpotifyCallbackView exchanges the code, then routes back here with ?spotifyConnected=1,
    // which auto-retries the export.
    await expect(page).toHaveURL(new RegExp(`/playlists/${PLAYLIST_ID}`))
    const link = page.getByRole('link', { name: /open\.spotify\.com/ })
    await expect(link).toBeVisible()
    await expect(page.getByText('3 songs matched on Spotify')).toBeVisible()
  })

  test('re-creating updates the same playlist rather than showing the connect prompt again', async ({ page }) => {
    await page.goto(`/playlists/${PLAYLIST_ID}`)
    await page.getByRole('button', { name: 'Create Spotify playlist' }).click()
    await page.getByRole('button', { name: 'Connect Spotify' }).click()
    await expect(page.getByRole('link', { name: /open\.spotify\.com/ })).toBeVisible()

    await page.getByRole('button', { name: 'Create Spotify playlist' }).click()
    await expect(page.getByText('Connect your Spotify account')).toHaveCount(0)
    await expect(page.getByRole('link', { name: /open\.spotify\.com/ })).toBeVisible()
  })

})

test.describe('Spotify playlist export (non-lead contributor)', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('mock-auth-contributor-id', 'cont-8f7e6d5c4b3a2019') // Nigel, not the lead of Road Trip Mix
    })
  })

  test('does not show the create button', async ({ page }) => {
    await page.goto(`/playlists/${PLAYLIST_ID}`)
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Road Trip Mix')
    await expect(page.getByRole('button', { name: 'Create Spotify playlist' })).toHaveCount(0)
  })
})
