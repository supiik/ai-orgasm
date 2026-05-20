import { createApp } from 'vue'
import './assets/index.css'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

async function bootstrap() {
  if (import.meta.env.VITE_MOCK === 'true') {
    const { worker } = await import('./mocks/browser')
    await worker.start({ onUnhandledRequest: 'warn' })
  } else {
    const keycloak = (await import('./keycloak')).default
    await keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256' })
  }

  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.mount('#app')
}

bootstrap()
