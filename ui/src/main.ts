import { createApp } from 'vue'
import './assets/index.css'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { i18n } from './i18n'
import { authMode } from './authMode'
import { installStaleChunkRecovery } from './staleChunkRecovery'
import { activeMaintenance, mountMaintenance } from './maintenance'

async function bootstrap() {
  // Maintenance announced in aquarium: show the static page instead of the app (no API calls, no sign-in).
  const maintenance = await activeMaintenance()
  if (maintenance) {
    mountMaintenance(maintenance)
    return
  }

  if (authMode === 'mock') {
    const { worker } = await import('./mocks/browser')
    await worker.start({ onUnhandledRequest: 'warn' })
  } else if (authMode === 'cognito') {
    // Cognito auth is an in-app overlay (CognitoLoginOverlay, gated in App.vue), not a
    // redirect-before-mount flow like Keycloak — just configure Amplify and mount normally.
    await import('./amplify')
  } else {
    const keycloak = (await import('./keycloak')).default
    await keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256' })
  }

  const app = createApp(App)
  app.use(createPinia())

  // Resolve the session before installing the router: `app.use(router)` kicks off the initial
  // navigation, and the /admin guard reads the auth store during it.
  const { useAuthStore } = await import('@/stores/auth')
  await useAuthStore().load()

  // Must be installed before the initial navigation so even a deep link into a route whose
  // chunk vanished with the last deploy recovers.
  installStaleChunkRecovery(router)
  app.use(router)
  app.use(i18n)

  app.mount('#app')
}

bootstrap()
