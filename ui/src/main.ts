import { createApp } from 'vue'
import './assets/index.css'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { authMode } from './authMode'

async function bootstrap() {
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
  app.use(router)

  const { useAuthStore } = await import('@/stores/auth')
  await useAuthStore().load()

  app.mount('#app')
}

bootstrap()
