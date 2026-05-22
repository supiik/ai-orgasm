import { test, expect } from '@playwright/test'

test('shows backend status from health endpoint', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('UP')).toBeVisible()
})

test('navigates to samples via sidebar', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: 'Samples' }).click()
  await expect(page).toHaveURL('/samples')
  await expect(page.getByRole('heading', { name: 'Samples' })).toBeVisible()
})
