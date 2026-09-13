import { test, expect } from '@playwright/test'
import { mockLogin, waitForMsw } from './helpers'

// The catalogue lookup (SongSearch.vue → `api.songs().search` → MSW's /api/v1/songs/search,
// standing in for the `search-songs` Lambda + MusicBrainz) used by both "create a song" forms.

test.describe('song catalogue search', () => {
  test.beforeEach(async ({ page }) => {
    await mockLogin(page)
    await waitForMsw(page)
  })

  test('search endpoint matches every term against artist or title (via MSW)', async ({ page }) => {
    const body = await page.evaluate(() => fetch('/api/v1/songs/search?q=radiohead%20creep').then(r => r.json()))
    expect(body.map((h: { name: string }) => h.name)).toEqual(['Creep'])
    const status = await page.evaluate(() => fetch('/api/v1/songs/search?q=x').then(r => r.status))
    expect(status).toBe(400)
  })

  test('pre-fills the new-song form from a chosen hit, leaving the fields editable', async ({ page }) => {
    await page.goto('/songs')
    await page.getByRole('button', { name: 'New song' }).click()

    const lookup = page.getByRole('combobox')
    await lookup.fill('radiohead')
    const results = page.getByRole('listbox', { name: 'Search results' })
    await expect(results.getByRole('option')).toHaveCount(2)
    await results.getByRole('button', { name: /Karma Police/ }).click()

    await expect(page.locator('#artist')).toHaveValue('Radiohead')
    await expect(page.locator('#name')).toHaveValue('Karma Police')
    await expect(page.locator('#album')).toHaveValue('OK Computer')
    await expect(page.locator('#releaseYear')).toHaveValue('1997')
    await expect(lookup).toHaveValue('')
    await expect(results).toBeHidden()

    await page.locator('#album').fill('OK Computer (Collector’s Edition)')
    await page.getByRole('button', { name: 'Create', exact: true }).click()
    await expect(page.getByRole('dialog')).toBeHidden()
    await expect(page.locator('table tbody tr').filter({ hasText: 'Karma Police' })).toContainText('OK Computer (Collector’s Edition)')
  })

  test('shows an empty state and an error state', async ({ page }) => {
    await page.goto('/songs')
    await page.getByRole('button', { name: 'New song' }).click()
    const lookup = page.getByRole('combobox')

    await lookup.fill('nothing matches this')
    await expect(page.getByText('No matches found.')).toBeVisible()

    await lookup.fill('unavailable')
    await expect(page.getByRole('alert')).toHaveText('Song lookup is unavailable right now.')
  })

  test('is not offered when editing an existing song', async ({ page }) => {
    await page.goto('/songs')
    await page.locator('table tbody tr').filter({ hasText: 'Radiohead' }).getByRole('button').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByRole('combobox')).toHaveCount(0)
  })

  test('pre-fills the nomination form and nominates the chosen song', async ({ page }) => {
    await page.goto('/playlists/play-2d3e4f5a6b7c8d90') // OPEN, deadline in the future
    await page.getByRole('button', { name: 'Nominate song' }).click()

    const dialog = page.getByRole('dialog')
    await dialog.getByRole('combobox').fill('oasis')
    await dialog.getByRole('button', { name: /Wonderwall/ }).click()

    await expect(dialog.locator('#nom-artist')).toHaveValue('Oasis')
    await expect(dialog.locator('#nom-name')).toHaveValue('Wonderwall')
    await expect(dialog.locator('#nom-album')).toHaveValue("(What's the Story) Morning Glory?")
    await expect(dialog.locator('#nom-year')).toHaveValue('1995')

    await dialog.getByRole('button', { name: 'Nominate', exact: true }).click()
    await expect(dialog).toBeHidden()
    await expect(page.getByText('Wonderwall', { exact: true })).toBeVisible()
  })
})
