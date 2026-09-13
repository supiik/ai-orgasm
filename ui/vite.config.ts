/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite'
import { configDefaults } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite'
import { fileURLToPath, URL } from 'node:url'
import { createRequire } from 'node:module'

const { version: appVersion } = createRequire(import.meta.url)('./package.json')

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const isMock = env.VITE_MOCK === 'true'

  return {
    define: {
      __APP_VERSION__: JSON.stringify(appVersion),
    },
    plugins: [
      tailwindcss(),
      vue(),
      // Pre-compiles src/locales/*.json at build time so the runtime-only vue-i18n build ships
      // (no message compiler in the bundle). Only the JSON catalogues — the glob must not catch
      // locales.test.ts, which the plugin would otherwise try to parse as a message file.
      VueI18nPlugin({ include: [fileURLToPath(new URL('./src/locales/*.json', import.meta.url))] }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      proxy: isMock ? undefined : {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    optimizeDeps: {
      include: ['@orgasm/backend-client'],
    },
    build: {
      outDir: 'dist',
      sourcemap: true,
    },
    test: {
      // Playwright specs live in e2e/ and share the *.spec.ts suffix — keep them out of Vitest.
      exclude: [...configDefaults.exclude, 'e2e/**'],
    },
  }
})
