import { test, expect } from '@playwright/test'
import { mockLogin } from './helpers'

// Mock mode treats the first seeded contributor (Thom Yorke, the one `mockLogin` uses) as the
// only member of the Cognito `admins` group — see isMockAdmin in src/mocks/handlers/contributors.ts.

test.describe('administration (admin user)', () => {
  test.beforeEach(async ({ page }) => {
    await mockLogin(page)
  })

  test('navigates to administration via sidebar and lists organizations', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: 'Administration' }).click()
    await expect(page).toHaveURL('/admin')
    await expect(page.getByRole('heading', { name: 'Administration' })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'Default Organization' })).toBeVisible()
    // First org is auto-selected and its seeded members listed with a link status
    await expect(page.getByRole('heading', { name: 'Members of Default Organization' })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'Thom Yorke' })).toBeVisible()
    await expect(page.getByText('Linked').first()).toBeVisible()
    await expect(page.getByText('Pending sign-up').first()).toBeVisible()
  })

  test('creates an organization, selects it, and adds a member', async ({ page }) => {
    await page.goto('/admin')
    await page.getByRole('button', { name: 'New organization' }).click()
    const orgDialog = page.getByRole('dialog')
    await orgDialog.getByLabel('Slug').fill('E2E-Org')
    await orgDialog.getByLabel('Name').fill('E2E Org')
    await orgDialog.getByLabel('Allowed email domain').fill('@e2e.test')
    await orgDialog.getByRole('button', { name: 'Create' }).click()

    const row = page.getByRole('row', { name: /e2e-org/ })
    await expect(row).toBeVisible()
    await expect(row.getByText('@e2e.test')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Members of E2E Org' })).toBeVisible()
    await expect(page.getByText('No members yet.')).toBeVisible()

    await page.getByRole('button', { name: 'Add member' }).click()
    const memberDialog = page.getByRole('dialog')
    await memberDialog.getByLabel('Name').fill('Eve Example')
    await memberDialog.getByLabel('Email').fill('eve@gmail.com')
    await memberDialog.getByRole('button', { name: 'Add', exact: true }).click()
    // Server-side allowedDomain gate surfaces its own message instead of a bare status
    await expect(memberDialog.getByText(/does not match organization "e2e-org"/)).toBeVisible()

    await memberDialog.getByLabel('Email').fill('eve@e2e.test')
    await memberDialog.getByRole('button', { name: 'Add', exact: true }).click()
    const member = page.getByRole('row', { name: /Eve Example/ })
    await expect(member).toBeVisible()
    await expect(member.getByText('Pending sign-up')).toBeVisible()
  })

  test('rejects a duplicate slug with the API message', async ({ page }) => {
    await page.goto('/admin')
    await page.getByRole('button', { name: 'New organization' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('Slug').fill('default')
    await dialog.getByLabel('Name').fill('Dup')
    await dialog.getByRole('button', { name: 'Create' }).click()
    await expect(dialog.getByText('Organization slug already in use: default')).toBeVisible()
  })
})

test.describe('administration (regular user)', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('mock-auth-contributor-id', 'cont-8f7e6d5c4b3a2019') // Nigel, not an admin
    })
  })

  test('hides the sidebar link and bounces /admin home', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Recent Songs' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Administration' })).toHaveCount(0)

    await page.goto('/admin')
    await expect(page).toHaveURL('/')
  })
})
