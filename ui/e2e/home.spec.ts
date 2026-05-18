import { test, expect } from '@playwright/test'

test('shows backend status from health endpoint', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('UP')).toBeVisible()
})

test('navigates to songs via sidebar', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: 'Songs' }).click()
  await expect(page).toHaveURL('/songs')
  await expect(page.getByRole('heading', { name: 'Songs' })).toBeVisible()
})

test('navigates to playlists via sidebar', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: 'Playlists' }).click()
  await expect(page).toHaveURL('/playlists')
  await expect(page.getByRole('heading', { name: 'Playlists' })).toBeVisible()
})

test('navigates to contributors via sidebar', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: 'Contributors' }).click()
  await expect(page).toHaveURL('/contributors')
  await expect(page.getByRole('heading', { name: 'Contributors' })).toBeVisible()
})
