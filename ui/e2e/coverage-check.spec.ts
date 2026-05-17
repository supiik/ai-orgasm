import { test, expect } from '@playwright/test'
import * as fs from 'node:fs'
import * as path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/**
 * Reads the router and asserts that each top-level route with a dedicated
 * view has a corresponding e2e spec file. This catches new entities that
 * were added to the router but never got an e2e test.
 *
 * Rules:
 *  - Only checks routes that map to a named view import (lazy or static).
 *  - Skips the root '/' and detail sub-routes (paths containing ':').
 *  - Expects a file  e2e/<route-segment>.spec.ts  to exist.
 */
test('every top-level view route has an e2e spec file', () => {
  const routerSrc = fs.readFileSync(
    path.resolve(__dirname, '../src/router/index.ts'),
    'utf-8',
  )

  // Extract path values from the routes array, e.g. '/playlists', '/songs'
  const routePaths = [...routerSrc.matchAll(/path:\s*'([^']+)'/g)]
    .map(m => m[1])
    .filter(p => p !== '/' && !p.includes(':'))  // skip root and detail routes

  const e2eDir = path.resolve(__dirname)
  const missing: string[] = []

  for (const routePath of routePaths) {
    const segment = routePath.replace(/^\//, '')           // 'playlists'
    const specFile = path.join(e2eDir, `${segment}.spec.ts`)
    if (!fs.existsSync(specFile)) {
      missing.push(`${segment}.spec.ts  (for route '${routePath}')`)
    }
  }

  expect(
    missing,
    `Missing e2e spec files:\n${missing.map(m => '  ' + m).join('\n')}\n\nAdd a spec file for each listed route.`,
  ).toHaveLength(0)
})
