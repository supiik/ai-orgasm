import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

test('navigates to guessing via sidebar', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/')
  await page.getByRole('link', { name: 'Guessing' }).click()
  await expect(page).toHaveURL('/guessing')
  await expect(page.getByRole('heading', { name: 'Guessing' })).toBeVisible()
})
