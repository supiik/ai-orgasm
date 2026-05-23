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
