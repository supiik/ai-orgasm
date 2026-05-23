import { onUnmounted } from 'vue'

export function useTokenRefresh() {
  if (import.meta.env.VITE_MOCK === 'true') return

  const REFRESH_INTERVAL = 4 * 60 * 1000

  let intervalId: number | undefined

  async function start() {
    const keycloak = (await import('@/keycloak')).default
    intervalId = window.setInterval(async () => {
      try {
        await keycloak.updateToken(300)
      } catch {
        clearInterval(intervalId)
        const { useAuthStore } = await import('@/stores/auth')
        useAuthStore().sessionExpired = true
      }
    }, REFRESH_INTERVAL)
  }

  onUnmounted(() => { if (intervalId) clearInterval(intervalId) })

  start()
}
