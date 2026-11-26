// Announcements and maintenance mode from aquarium (static JSON on CloudFront, see aquarium backend/src/notices.ts).
// customers-orgasm.json holds the notices for orgasm users; during maintenance the app shows a static page and calls no API.
import { createApp } from 'vue'
import { activeNotices, isNotice } from '@supiik/pico-ui/notices'
import type { PublicNotice } from '@supiik/pico-ui/notices'
import MaintenanceScreen from '@supiik/pico-ui/MaintenanceScreen.vue'
import outputs from '../amplify_outputs.json'

/** base URL of the notice files (amplify_outputs.json custom.noticesUrl); empty in mock/dev without a deployed backend */
export const noticesUrl = (outputs as { custom?: { noticesUrl?: string } }).custom?.noticesUrl ?? ''
export const NOTICES_FILE = 'customers-orgasm.json'

/** active maintenance notice, or null (also on any network error or after 3 s — notices must never block the app) */
export async function activeMaintenance(fetchFn: typeof fetch = (...a) => fetch(...a)): Promise<PublicNotice | null> {
  if (!noticesUrl) return null
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), 3000)
  try {
    const r = await fetchFn(`${noticesUrl}/${NOTICES_FILE}`, { cache: 'no-cache', signal: ctrl.signal })
    if (!r.ok) return null
    const data = (await r.json()) as unknown
    const list = Array.isArray(data) ? data.filter(isNotice) : []
    return activeNotices(list, Date.now(), 'orgasm').find((n) => n.kind === 'maintenance') ?? null
  } catch {
    return null
  } finally {
    clearTimeout(timer)
  }
}

/** shows only the maintenance page; checks every minute and reloads the app once maintenance is over */
export function mountMaintenance(notice: PublicNotice) {
  createApp(MaintenanceScreen, { notice, title: 'orgasm' }).mount('#app')
  setInterval(async () => {
    if (!(await activeMaintenance())) location.reload()
  }, 60_000)
}
