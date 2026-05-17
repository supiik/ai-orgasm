import { test, expect } from '@playwright/test'

test('shows backend status from health endpoint', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('UP')).toBeVisible()
})

test('navigates to about page', async ({ page }) => {
  await page.goto('/')
  await page.goto('/about')
  await expect(page.getByRole('heading', { name: 'About' })).toBeVisible()
})
