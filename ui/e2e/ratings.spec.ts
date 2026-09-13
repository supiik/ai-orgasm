import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

// Summer Classics (published, BEST_SONG) — Thom is logged in via mockLogin and nominated Wonderwall,
// so Creep and Smells Like Teen Spirit are the two songs he can rate.
const PLAYLIST_URL = '/playlists/play-d7e8f9a0b1c2d3e4'

function starButtons(page: import('@playwright/test').Page, song: string) {
  return page.locator('div.flex.items-center', { has: page.getByText(song, { exact: true }) }).getByRole('button')
}

test.describe('song ratings', () => {
  test.beforeEach(async ({ page }) => {
    await mockLogin(page)
    await page.goto(PLAYLIST_URL)
    await expect(page.getByRole('heading', { name: 'Song Ratings' })).toBeVisible()
  })

  test('shows that nothing is saved until the rating is complete', async ({ page }) => {
    await expect(page.getByText(/Not saved yet/)).toBeVisible()
    await expect(starButtons(page, 'Creep')).toHaveCount(1)
  })

  test('saved stars survive leaving and reopening the playlist', async ({ page }) => {
    const creepStar = starButtons(page, 'Creep').first()
    await creepStar.click()
    await expect(page.getByText('Your ratings are saved.')).toBeVisible()
    await expect(creepStar.locator('svg')).toHaveClass(/fill-yellow-500/)

    // Client-side navigation away and back remounts the view and reloads ratings from the API
    // (a hard reload would also wipe MSW's in-memory store, which is not what we're testing).
    await page.getByRole('link', { name: 'Playlists' }).click()
    await expect(page).toHaveURL('/playlists')
    await page.getByText('Summer Classics').click()
    await expect(page).toHaveURL(PLAYLIST_URL)

    await expect(page.getByRole('heading', { name: 'Song Ratings' })).toBeVisible()
    await expect(page.getByText('Your ratings are saved.')).toBeVisible()
    await expect(starButtons(page, 'Creep').first().locator('svg')).toHaveClass(/fill-yellow-500/)
    await expect(starButtons(page, 'Smells Like Teen Spirit').first().locator('svg')).not.toHaveClass(/fill-yellow-500/)
    await expect(page.locator('span.rounded-full', { hasText: 'Thom Yorke' })).toHaveText(/Thom Yorke\s*1/)
  })
})
