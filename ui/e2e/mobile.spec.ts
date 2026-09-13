import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

// Phone-width shell: the persistent sidebar is hidden and navigation lives in a drawer opened
// from the top bar's hamburger button.
test.use({ viewport: { width: 390, height: 844 } })

test('hides the sidebar and shows the hamburger on mobile', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await expect(page.getByRole('button', { name: 'Open menu' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Songs' })).toBeHidden()
})

test('navigates via the mobile drawer and closes it afterwards', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Open menu' }).click()
  const drawer = page.getByRole('dialog', { name: 'ORGAnized Spotify Mediabuilding' })
  await expect(drawer).toBeVisible()
  await drawer.getByRole('link', { name: 'Songs' }).click()
  await expect(page).toHaveURL('/songs')
  await expect(page.getByRole('heading', { name: 'Songs' })).toBeVisible()
  await expect(drawer).toBeHidden()
})

test('closes the drawer with the close button', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Open menu' }).click()
  const drawer = page.getByRole('dialog', { name: 'ORGAnized Spotify Mediabuilding' })
  await expect(drawer).toBeVisible()
  await drawer.getByRole('button', { name: 'Close menu' }).click()
  await expect(drawer).toBeHidden()
})

test('page body does not scroll horizontally on mobile', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow).toBe(0)
})
