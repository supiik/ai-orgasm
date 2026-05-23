import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ContributorResponse } from '@orgasm/backend-client'

export const useAuthStore = defineStore('auth', () => {
  const isMock = import.meta.env.VITE_MOCK === 'true'
  const currentContributor = ref<ContributorResponse | null>(null)
  const loading = ref(false)

  async function load() {
    if (isMock) return
    loading.value = true
    try {
      const { api } = await import('@/api')
      const { data } = await api.contributors().me()
      currentContributor.value = data
    } catch {
      currentContributor.value = null
    } finally {
      loading.value = false
    }
  }

  function isLeadOf(leadId: string | null | undefined): boolean {
    if (isMock || !currentContributor.value || !leadId) return false
    return currentContributor.value.id === leadId
  }

  return { currentContributor, loading, load, isLeadOf }
})
