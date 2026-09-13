import type { Page } from '@playwright/test'

export async function mockLogin(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('mock-auth-contributor-id', 'cont-1a2b3c4d5e6f7089')
  })
}

export async function ensureLoggedIn(page: Page) {
  const overlay = page.locator('.fixed.inset-0.z-50')
  if (await overlay.isVisible({ timeout: 1000 }).catch(() => false)) {
    await page.getByText('Thom Yorke').click()
  }
}

/**
 * Loads the app and waits until MSW's Service Worker controls the page, so browser-side
 * `fetch` calls (the only kind MSW can intercept — see CLAUDE.md) are mocked from the first one.
 */
export async function waitForMsw(page: Page) {
  await page.goto('/')
  await page.waitForFunction(() => navigator.serviceWorker?.controller !== null)
}
