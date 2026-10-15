import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

// Nigel Godrich (cont-8f7e6d5c4b3a2019) is the seeded org admin — deliberately not Thom
// (the seeded platform admin that `mockLogin` logs in as), so the two roles stay distinguishable.
async function mockLoginAsOrgAdmin(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('mock-auth-contributor-id', 'cont-8f7e6d5c4b3a2019')
  })
}

test.describe('organization settings (org admin)', () => {
  test('hides the nav entry and shows the forbidden message for a non-org-admin', async ({ page }) => {
    await mockLogin(page) // Thom — platform admin, but not an org admin
    await page.goto('/')
    await expect(page.getByRole('link', { name: 'Organization' })).toHaveCount(0)

    await page.goto('/organization')
    await expect(page.getByText('You are not an organization admin.')).toBeVisible()
  })

  test('navigates via sidebar and lists members with roles', async ({ page }) => {
    await mockLoginAsOrgAdmin(page)
    await page.goto('/')
    await page.getByRole('link', { name: 'Organization' }).click()
    await expect(page).toHaveURL('/organization')
    await expect(page.getByRole('heading', { name: 'Organization', exact: true })).toBeVisible()

    const nigelRow = page.getByRole('row', { name: /Nigel Godrich/ })
    await expect(nigelRow.getByText('Organization admin')).toBeVisible()
    const thomRow = page.getByRole('row', { name: /Thom Yorke/ })
    await expect(thomRow.getByText('Member', { exact: true })).toBeVisible()
  })

  test('generates, copies, and revokes the invite link', async ({ page }) => {
    await mockLoginAsOrgAdmin(page)
    await page.goto('/organization')
    await expect(page.getByText('No invite link yet.')).toBeVisible()

    await page.getByRole('button', { name: 'Generate link' }).click()
    const linkText = page.locator('span.font-mono')
    await expect(linkText).toBeVisible()
    await expect(linkText).toContainText('/join?token=')

    await page.getByRole('button', { name: 'Revoke' }).click()
    await expect(page.getByText('No invite link yet.')).toBeVisible()
  })

  test('promotes and demotes a member', async ({ page }) => {
    await mockLoginAsOrgAdmin(page)
    await page.goto('/organization')

    const jonnyRow = page.getByRole('row', { name: /Jonny Greenwood/ })
    await expect(jonnyRow.getByText('Member', { exact: true })).toBeVisible()

    await jonnyRow.getByRole('button', { name: 'Make organization admin' }).click()
    await expect(jonnyRow.getByText('Organization admin')).toBeVisible()

    await jonnyRow.getByRole('button', { name: 'Remove organization admin' }).click()
    await expect(jonnyRow.getByText('Member', { exact: true })).toBeVisible()
  })
})
