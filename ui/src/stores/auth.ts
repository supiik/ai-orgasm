import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ContributorResponse } from '@orgasm/backend-client'

const MOCK_AUTH_KEY = 'mock-auth-contributor-id'

export const useAuthStore = defineStore('auth', () => {
  const isMock = import.meta.env.VITE_MOCK === 'true'
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

  async function logout() {
    if (isMock) {
      const { clearMockCurrentContributor } = await import('@/mocks/handlers/contributors')
      clearMockCurrentContributor()
      sessionStorage.removeItem(MOCK_AUTH_KEY)
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
    } else {
      import('@/keycloak').then(m => m.default.login())
    }
  }

  function isLeadOf(leadId: string | null | undefined): boolean {
    if (!currentContributor.value || !leadId) return false
    return currentContributor.value.id === leadId
  }

  return { currentContributor, isAuthenticated, sessionExpired, loading, load, mockLogin, logout, reauthenticate, isLeadOf }
})
