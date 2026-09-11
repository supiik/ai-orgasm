import { createApp } from 'vue'
import './assets/index.css'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { authMode } from './authMode'

// TEMPORARY diagnostic: surface otherwise-invisible runtime errors on-page (this env has no
// browser console access during debugging). Remove once the Cognito blank-screen issue is found.
function showDebugBanner(msg: string) {
  const el = document.createElement('pre')
  el.style.cssText =
    'position:fixed;inset:0;z-index:99999;background:#fff;color:#c00;padding:16px;overflow:auto;font-size:12px;white-space:pre-wrap;margin:0;'
  el.textContent = msg
  document.body.appendChild(el)
}
window.addEventListener('error', (e) => showDebugBanner(`[window error] ${e.error?.stack ?? e.message}`))
window.addEventListener('unhandledrejection', (e) => {
  const reason = e.reason
  showDebugBanner(`[unhandled rejection] ${reason?.stack ?? String(reason)}`)
})

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
  app.config.errorHandler = (err) => showDebugBanner(`[vue error] ${err instanceof Error ? (err.stack ?? err.message) : String(err)}`)
  app.use(createPinia())
  app.use(router)

  const { useAuthStore } = await import('@/stores/auth')
  await useAuthStore().load()

  app.mount('#app')
}

bootstrap().catch((e) => showDebugBanner(`[bootstrap error] ${e instanceof Error ? (e.stack ?? e.message) : String(e)}`))
