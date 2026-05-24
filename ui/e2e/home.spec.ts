import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

test('shows recent songs on home page', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Recent Songs' })).toBeVisible()
  await expect(page.getByText('Creep')).toBeVisible()
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
