import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

test('shows recent songs on home page', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Recent Songs' })).toBeVisible()
  await expect(page.getByText('Creep')).toBeVisible()
})

test('does not render the same cover artwork twice when iTunes returns a shared match', async ({ page }) => {
  // src/mocks/handlers/artwork.ts deliberately gives "Smells Like Teen Spirit" and "Wonderwall"
  // (two of the three seeded songs) the same generic fallback artwork, mirroring how the real
  // iTunes search can return the same (often wrong) match for different queries — only the
  // first of the two in display order should render it; the other falls back to the
  // placeholder icon instead of showing a duplicate cover.
  await mockLogin(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Recent Songs' })).toBeVisible()

  await expect(page.locator('img[src="https://mock-artwork.test/generic-300x300bb.jpg"]')).toHaveCount(1)
  await expect(page.locator('img[src="https://mock-artwork.test/creep-300x300bb.jpg"]')).toHaveCount(1)
})

test('navigates to songs via sidebar', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await page.getByRole('link', { name: 'Songs' }).click()
  await expect(page).toHaveURL('/songs')
  await expect(page.getByRole('heading', { name: 'Songs' })).toBeVisible()
})

test('navigates to playlists via sidebar', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await page.getByRole('link', { name: 'Playlists' }).click()
  await expect(page).toHaveURL('/playlists')
  await expect(page.getByRole('heading', { name: 'Playlists' })).toBeVisible()
})

test('navigates to contributors via sidebar', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await page.getByRole('link', { name: 'Contributors' }).click()
  await expect(page).toHaveURL('/contributors')
  await expect(page.getByRole('heading', { name: 'Contributors' })).toBeVisible()
})

test('navigates to stats via sidebar', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await page.getByRole('link', { name: 'Stats' }).click()
  await expect(page).toHaveURL('/stats')
  await expect(page.getByRole('heading', { name: 'Stats' })).toBeVisible()
})

test('shows the current contributor\'s organization above their name in the sidebar', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await expect(page.getByText('Default Organization')).toBeVisible()
  await expect(page.getByText('Thom Yorke')).toBeVisible()
})
