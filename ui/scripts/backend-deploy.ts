// Nasadenie backendu (Amplify Gen 2) len pri zmene – šetrí minúty buildu v Amplify (platia sa po minútach).
// Odtlačok = SHA-256 súborov backendu (INPUTS) a nastavení prostredia, ktoré backend číta (ENV). Pri nasadení ho
// amplify/backend.ts zapíše do výstupov (custom.backendHash). Build najprv lacno stiahne výstupy nasadeného backendu
// (`ampx generate outputs`); ak odtlačok sedí, nasadenie preskočí (amplify_outputs.json je aj tak aktuálny).
//
// Vynútiť nasadenie (FORCE_BACKEND_DEPLOY=1 v premenných Amplify na jeden build):
//   - zmena tajnej premennej (do odtlačku sa zámerne nedávajú – výstupy sú verejné),
//   - zmena hodnôt z iných aplikácií, ktoré sa čítajú až pri nasadení (napr. SSM /aquarium/<vetva>/apps – nová admin aplikácia
//     v registri sa v menu objaví až po nasadení).
// Spustenie: node scripts/backend-deploy.ts (amplify.yml, fáza backend).
import { createHash } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

/** súbory a priečinky, z ktorých vzniká backend (relatívne ku koreňu projektu) */
const INPUTS = ['amplify', 'package-lock.json']
/** nastavenia prostredia, ktoré backend číta (bez tajomstiev) */
const ENV = ['AWS_BRANCH', 'ALLOWED_ORIGINS', 'APP_ENV', 'LICENSE_ENFORCEMENT', 'LOG_LEVEL', 'PUBLIC_FN_RESERVED_CONCURRENCY', 'SONG_SEARCH_PROVIDER', 'SONG_SEARCH_USER_AGENT', 'SPOTIFY_CLIENT_ID', 'SPOTIFY_REDIRECT_URI']
const SKIP_DIRS = new Set(['node_modules', 'dist', '.synth', '.amplify'])

const root = fileURLToPath(new URL('..', import.meta.url))

function files(path: string): string[] {
  const full = join(root, path)
  if (!existsSync(full)) return []
  if (!statSync(full).isDirectory()) return [path]
  return readdirSync(full, { withFileTypes: true })
    .filter((e) => !e.name.startsWith('.') && !SKIP_DIRS.has(e.name))
    .flatMap((e) => files(relative(root, join(full, e.name))))
}

/** odtlačok backendu – rovnaký pri builde aj v amplify/backend.ts (pri syntéze) */
export function backendHash(): string {
  const h = createHash('sha256')
  for (const f of INPUTS.flatMap(files).sort()) {
    h.update(`${f}\0`)
    h.update(readFileSync(join(root, f)))
    h.update('\0')
  }
  for (const k of ENV) h.update(`${k}=${process.env[k] ?? ''}\0`)
  return h.digest('hex').slice(0, 32)
}

function npx(args: string[]): boolean {
  return spawnSync('npx', args, { cwd: root, stdio: 'inherit' }).status === 0
}

function main() {
  const branch = process.env.AWS_BRANCH ?? ''
  const appId = process.env.AWS_APP_ID ?? ''
  const hash = backendHash()
  const force = /^(1|true|yes)$/i.test(process.env.FORCE_BACKEND_DEPLOY ?? '')
  if (!force && npx(['ampx', 'generate', 'outputs', '--branch', branch, '--app-id', appId])) {
    try {
      const deployed = (JSON.parse(readFileSync(join(root, 'amplify_outputs.json'), 'utf8')) as { custom?: { backendHash?: string } }).custom?.backendHash
      if (deployed === hash) {
        console.log(`backend bez zmeny (${hash}) – nasadenie preskočené`)
        return
      }
      console.log(`backend sa zmenil (${deployed ?? 'bez odtlačku'} → ${hash}) – nasadzujem`)
    } catch {
      console.log('výstupy nasadeného backendu sa nedajú prečítať – nasadzujem')
    }
  } else {
    console.log(force ? 'FORCE_BACKEND_DEPLOY – nasadzujem' : 'backend ešte nie je nasadený – nasadzujem')
  }
  if (!npx(['ampx', 'pipeline-deploy', '--branch', branch, '--app-id', appId])) process.exit(1)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main()
