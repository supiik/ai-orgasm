import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { ContributorResponse } from '@orgasm/backend-client'
import { authMode } from '@/authMode'

const MOCK_AUTH_KEY = 'mock-auth-contributor-id'

export const useAuthStore = defineStore('auth', () => {
  const isMock = authMode === 'mock'
  const currentContributor = ref<ContributorResponse | null>(null)
  const isAuthenticated = ref(false)
  /**
   * Unlocks the /admin section. Cognito: the signed ID token's `cognito:groups` contains
   * `admins` (the Lambda API re-checks the same claim server-side — this flag only decides what
   * to render). Mock: a fixed seeded contributor. Keycloak: never — the Spring backend has no
   * admin endpoints.
   */
  const isAdmin = ref(false)
  const sessionExpired = ref(false)
  const loading = ref(false)
  /**
   * Name of the organization the current contributor belongs to, shown above the current-user
   * block in SidebarNav. Mock/Keycloak: resolved from the public `GET /api/v1/organizations`
   * list (mock has exactly one seeded org; Keycloak matches the JWT's `tenant_id` claim against
   * `Organization.id`). Cognito: the ID token carries no tenant claim at all, so it comes
   * straight off `getCurrentContributor()`'s response — `get-current-contributor`'s handler
   * enriches it server-side with a `findOrganizationById(ctx.tenantId)` lookup, same
   * "lookup-at-read" pattern as `leadContributorName` elsewhere in this codebase.
   */
  const organizationName = ref<string | null>(null)

  /**
   * Unlocks the organization-settings nav entry (invite link + member promote/demote). Backed by
   * `ContributorResponse.isOrgAdmin`, present in all 3 modes' `currentContributor` payload — unlike
   * `organizationName`, this needs no mode-specific resolution. Not in the generated SDK type, same
   * type-extension-over-the-SDK-type workaround as `leadContributorName`/`spotifyPlaylistUrl`.
   */
  const isOrgAdmin = computed(() =>
    (currentContributor.value as (ContributorResponse & { isOrgAdmin?: boolean }) | null)?.isOrgAdmin ?? false)

  async function load() {
    loading.value = true
    try {
      if (isMock) {
        const savedId = sessionStorage.getItem(MOCK_AUTH_KEY)
        if (!savedId) return
        const { setMockCurrentContributor, db, isMockAdmin } = await import('@/mocks/handlers/contributors')
        setMockCurrentContributor(savedId)
        const saved = db.find(c => c.id === savedId)
        if (saved) {
          currentContributor.value = saved as ContributorResponse
          isAuthenticated.value = true
          isAdmin.value = isMockAdmin(savedId)
          await loadOrganizationName()
        }
        return
      }
      if (authMode === 'cognito') {
        if (!(await loadCognitoSession())) return
        const { getCurrentContributor } = await import('@/lambdaApi')
        const contributor = await getCurrentContributor().catch(() => null)
        currentContributor.value = contributor as ContributorResponse | null
        organizationName.value = contributor?.organizationName ?? null
        return
      }
      const { api } = await import('@/api')
      const { data } = await api.contributors().me()
      currentContributor.value = data
      isAuthenticated.value = true
      await loadOrganizationName()
    } catch {
      currentContributor.value = null
      isAuthenticated.value = !isMock
    } finally {
      loading.value = false
    }
  }

  /**
   * Mock: only one seeded org, so it's always `[0]`. Keycloak: `keycloak.tokenParsed` is
   * already decoded by keycloak-js once authenticated — reads its `tenant_id` claim (see
   * `TenantResolverFilter.java`) and matches it against the public org list's `id`. Never
   * throws; a lookup failure just leaves the name unset.
   */
  async function loadOrganizationName() {
    try {
      if (isMock) {
        const { organizationsDb } = await import('@/mocks/handlers/organizations')
        organizationName.value = organizationsDb[0]?.name ?? null
        return
      }
      const keycloak = (await import('@/keycloak')).default
      const tenantId = keycloak.tokenParsed?.tenant_id as number | undefined
      if (tenantId == null) return
      const res = await fetch('/api/v1/organizations')
      if (!res.ok) return
      const orgs = await res.json() as { id: number; name: string }[]
      organizationName.value = orgs.find(o => o.id === tenantId)?.name ?? null
    } catch {
      organizationName.value = null
    }
  }

  /**
   * Reads the current Cognito session into isAuthenticated/isAdmin. Unlike Keycloak's
   * login-required redirect, the app can mount with no session at all — that maps to
   * isAuthenticated=false (overlay shows sign-in), not an exception. Returns whether a session
   * exists; CognitoLoginOverlay calls it again right after sign-in.
   */
  async function loadCognitoSession(): Promise<boolean> {
    const { fetchAuthSession } = await import('aws-amplify/auth')
    const session = await fetchAuthSession().catch(() => null)
    const idToken = session?.tokens?.idToken
    if (!idToken) return false
    isAuthenticated.value = true // signed in; contributor may still be unlinked
    const groups = idToken.payload['cognito:groups']
    isAdmin.value = Array.isArray(groups) && groups.includes('admins')
    return true
  }

  async function mockLogin(contributor: ContributorResponse) {
    const { setMockCurrentContributor, isMockAdmin } = await import('@/mocks/handlers/contributors')
    setMockCurrentContributor(contributor.id!)
    sessionStorage.setItem(MOCK_AUTH_KEY, contributor.id!)
    currentContributor.value = contributor
    isAuthenticated.value = true
    isAdmin.value = isMockAdmin(contributor.id!)
    await loadOrganizationName()
  }

  /** Called by CognitoLoginOverlay once a Contributor is confirmed linked. */
  function setCognitoContributor(contributor: ContributorResponse) {
    currentContributor.value = contributor
    isAuthenticated.value = true
    organizationName.value = (contributor as ContributorResponse & { organizationName?: string }).organizationName ?? null
  }

  async function logout() {
    if (isMock) {
      const { clearMockCurrentContributor } = await import('@/mocks/handlers/contributors')
      clearMockCurrentContributor()
      sessionStorage.removeItem(MOCK_AUTH_KEY)
      currentContributor.value = null
      isAuthenticated.value = false
      isAdmin.value = false
      organizationName.value = null
    } else if (authMode === 'cognito') {
      const { signOut } = await import('aws-amplify/auth')
      await signOut()
      currentContributor.value = null
      isAuthenticated.value = false
      isAdmin.value = false
      organizationName.value = null
    } else {
      const keycloak = (await import('@/keycloak')).default
      await keycloak.logout({ redirectUri: window.location.origin })
    }
  }

  function reauthenticate() {
    sessionExpired.value = false
    if (isMock) {
      isAuthenticated.value = false
    } else if (authMode === 'cognito') {
      currentContributor.value = null
      isAuthenticated.value = false
      isAdmin.value = false
      organizationName.value = null
    } else {
      import('@/keycloak').then(m => m.default.login())
    }
  }

  function isLeadOf(leadId: string | null | undefined): boolean {
    if (!currentContributor.value || !leadId) return false
    return currentContributor.value.id === leadId
  }

  return { currentContributor, isAuthenticated, isAdmin, sessionExpired, loading, organizationName, isOrgAdmin, load, loadCognitoSession, mockLogin, setCognitoContributor, logout, reauthenticate, isLeadOf }
})
