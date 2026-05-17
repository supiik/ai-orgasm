import { test, expect } from '@playwright/test'

test('shows backend status from health endpoint', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('UP')).toBeVisible()
})

test('navigates to songs via sidebar', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: 'Songs' }).click()
  await expect(page.getByRole('heading', { name: 'Songs' })).toBeVisible()
})
