import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ContributorResponse } from '@orgasm/backend-client'
import { authMode } from '@/authMode'

const MOCK_AUTH_KEY = 'mock-auth-contributor-id'

export const useAuthStore = defineStore('auth', () => {
  const isMock = authMode === 'mock'
  const currentContributor = ref<ContributorResponse | null>(null)
  const isAuthenticated = ref(false)
  const sessionExpired = ref(false)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      if (isMock) {
        const savedId = sessionStorage.getItem(MOCK_AUTH_KEY)
        if (!savedId) return
        const { setMockCurrentContributor, db } = await import('@/mocks/handlers/contributors')
        setMockCurrentContributor(savedId)
        const saved = db.find(c => c.id === savedId)
        if (saved) {
          currentContributor.value = saved as ContributorResponse
          isAuthenticated.value = true
        }
        return
      }
      if (authMode === 'cognito') {
        // Unlike Keycloak's login-required redirect, the app can mount here with no session at
        // all — that must map to isAuthenticated=false (overlay shows sign-in), not an exception.
        const { fetchAuthSession } = await import('aws-amplify/auth')
        const session = await fetchAuthSession().catch(() => null)
        if (!session?.tokens?.idToken) return
        isAuthenticated.value = true // signed in; contributor may still be unlinked
        const { getCurrentContributor } = await import('@/lambdaApi')
        currentContributor.value = (await getCurrentContributor().catch(() => null)) as ContributorResponse | null
        return
      }
      const { api } = await import('@/api')
      const { data } = await api.contributors().me()
      currentContributor.value = data
      isAuthenticated.value = true
    } catch {
      currentContributor.value = null
      isAuthenticated.value = !isMock
    } finally {
      loading.value = false
    }
  }

  async function mockLogin(contributor: ContributorResponse) {
    const { setMockCurrentContributor } = await import('@/mocks/handlers/contributors')
    setMockCurrentContributor(contributor.id!)
    sessionStorage.setItem(MOCK_AUTH_KEY, contributor.id!)
    currentContributor.value = contributor
    isAuthenticated.value = true
  }

  /** Called by CognitoLoginOverlay once a Contributor is confirmed linked. */
  function setCognitoContributor(contributor: ContributorResponse) {
    currentContributor.value = contributor
    isAuthenticated.value = true
  }

  async function logout() {
    if (isMock) {
      const { clearMockCurrentContributor } = await import('@/mocks/handlers/contributors')
      clearMockCurrentContributor()
      sessionStorage.removeItem(MOCK_AUTH_KEY)
      currentContributor.value = null
      isAuthenticated.value = false
    } else if (authMode === 'cognito') {
      const { signOut } = await import('aws-amplify/auth')
      await signOut()
      currentContributor.value = null
      isAuthenticated.value = false
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
    } else {
      import('@/keycloak').then(m => m.default.login())
    }
  }

  function isLeadOf(leadId: string | null | undefined): boolean {
    if (!currentContributor.value || !leadId) return false
    return currentContributor.value.id === leadId
  }

  return { currentContributor, isAuthenticated, sessionExpired, loading, load, mockLogin, setCognitoContributor, logout, reauthenticate, isLeadOf }
})
