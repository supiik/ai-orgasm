import { test, expect } from '@playwright/test'
import { waitForMsw } from './helpers'

function browserFetch(page: import('@playwright/test').Page, input: string, init?: RequestInit) {
  return page.evaluate(
    ({ input, init }) =>
      fetch(input, init).then(async (r) => ({
        status: r.status,
        body: r.status === 204 ? null : await r.json().catch(() => null),
      })),
    { input, init },
  )
}

test.describe('org-join invite link redemption', () => {
  test('stashes the invite token from the URL and redirects home', async ({ page }) => {
    // A fresh, never-logged-in session: the /join route's guard runs regardless of the login
    // overlay (navigation guards resolve independently of what's currently rendered).
    await page.goto('/join?token=abc123')
    await expect(page).toHaveURL('/')
    await expect(page.evaluate(() => sessionStorage.getItem('pending-invite-token'))).resolves.toBe('abc123')
  })

  test('shows the joining-organization confirmation and registers when the stashed token resolves', async ({ page }) => {
    // Everything happens in one continuous page load on purpose: MSW's mock DB lives in page-level
    // module state (not the Service Worker itself), so it resets on every full navigation — a
    // runtime-generated invite token would be lost by a page.goto between "admin generates it" and
    // "new user redeems it". Logging out doesn't reload the page (see auth.ts's mock branch), so
    // the token survives; only the stashing step that a real `/join` visit would do is simulated
    // directly (that mechanism is covered on its own in the previous test).
    await waitForMsw(page)
    await page.getByText('Nigel Godrich').click() // the seeded org admin
    const created = await browserFetch(page, '/api/v1/organizations/invite-link', { method: 'POST' })
    expect(created.status).toBe(200)
    const token = created.body.inviteToken as string
    expect(token).toBeTruthy()

    await page.evaluate((t) => sessionStorage.setItem('pending-invite-token', t), token)
    await page.getByRole('button', { name: 'Switch User' }).click()

    // MockLoginOverlay remounted on logout, picked up the stashed token: register tab
    // auto-selected, org picker replaced by a confirmation.
    await expect(page.getByText("You're joining Default Organization.")).toBeVisible()
    await page.getByLabel('Name').fill('E2E Invitee')
    await page.locator('form').getByRole('button', { name: 'Register' }).click()

    // Successful registration logs the new contributor straight in.
    await expect(page.getByText('E2E Invitee')).toBeVisible()
  })

  test('falls back to the organization picker for an invalid token', async ({ page }) => {
    await page.goto('/join?token=not-a-real-token')
    await expect(page).toHaveURL('/')
    await expect(page.getByText("You're joining")).toHaveCount(0)
    await expect(page.getByLabel('Organization')).toBeVisible()
  })
})
