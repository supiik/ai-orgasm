import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

// Language switching + persistence. The default Playwright locale is en-US, so every other spec
// keeps asserting English; these tests opt into Slovak explicitly.

test('switches language from the sidebar and persists it in a cookie', async ({ page, context }) => {
  await mockLogin(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Recent Songs' })).toBeVisible()

  await page.getByRole('combobox', { name: 'Language' }).click()
  await page.getByRole('option', { name: 'Slovenčina' }).click()

  await expect(page.getByRole('heading', { name: 'Nedávne skladby' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Skladby' })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'sk')

  const cookie = (await context.cookies()).find(c => c.name === 'locale')
  expect(cookie?.value).toBe('sk')
  expect(cookie?.sameSite).toBe('Lax')

  // Survives a full reload — read back from the cookie, not from in-memory state.
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Nedávne skladby' })).toBeVisible()

  // And back to English — the switcher is labelled in the *current* language now.
  await page.getByRole('combobox', { name: 'Jazyk' }).click()
  await page.getByRole('option', { name: 'English' }).click()
  await expect(page.getByRole('heading', { name: 'Recent Songs' })).toBeVisible()
})

test.describe('browser language detection', () => {
  test.use({ locale: 'sk-SK' })

  test('picks Slovak from the browser language when no cookie is set', async ({ page }) => {
    await mockLogin(page)
    await page.goto('/playlists')
    await expect(page.getByRole('heading', { name: 'Playlisty' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Nový playlist' })).toBeVisible()
  })

  test('an explicit cookie wins over the browser language', async ({ page, context }) => {
    await context.addCookies([{ name: 'locale', value: 'en', url: 'http://localhost:5173' }])
    await mockLogin(page)
    await page.goto('/playlists')
    await expect(page.getByRole('heading', { name: 'Playlists' })).toBeVisible()
  })
})
