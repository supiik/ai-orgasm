import { onUnmounted } from 'vue'
import { authMode } from '@/authMode'

export function useTokenRefresh() {
  // Mock needs no refresh; Amplify's fetchAuthSession() (called per-request in api-lambda.ts)
  // auto-refreshes Cognito tokens near expiry on its own — no periodic timer needed there.
  if (authMode === 'mock' || authMode === 'cognito') return

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
