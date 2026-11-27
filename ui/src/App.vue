<script setup lang="ts">
import { useNotices } from '@supiik/pico-ui/notices'
import NoticeBar from '@supiik/pico-ui/NoticeBar.vue'
import NoticeBell from '@supiik/pico-ui/NoticeBell.vue'
import MaintenanceScreen from '@supiik/pico-ui/MaintenanceScreen.vue'
import { noticesUrl, NOTICES_FILE } from './maintenance'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import LicenseBanner from '@/components/LicenseBanner.vue'
import { useI18n } from 'vue-i18n'
import { Menu } from 'lucide-vue-next'
import { useTokenRefresh } from '@/composables/useTokenRefresh'
import { useAuthStore } from '@/stores/auth'
import { authMode } from '@/authMode'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent } from '@/components/ui/sheet'
import SidebarNav from '@/components/SidebarNav.vue'
import MockLoginOverlay from '@/components/MockLoginOverlay.vue'
import CognitoLoginOverlay from '@/components/CognitoLoginOverlay.vue'

useTokenRefresh()

const { t } = useI18n()
const APP_TITLE = computed(() => t('app.title'))

// Always-visible top bar (same as aquarium's AppShell): current section on the left, announcement bell on the right.
const SECTION: Record<string, string> = {
  home: 'nav.home', playlists: 'nav.playlists', 'playlist-detail': 'nav.playlists', songs: 'nav.songs', 'song-detail': 'nav.songs',
  contributors: 'nav.contributors', 'contributor-detail': 'nav.contributors', guessing: 'nav.guessing', stats: 'nav.stats',
  organization: 'nav.organization', admin: 'nav.admin',
}

// Mobile nav drawer (< md). Closed on every route change, not just link clicks, so back/forward
// navigation and programmatic pushes (e.g. row clicks in a table) never leave it hanging open.
const route = useRoute()
const mobileNavOpen = ref(false)
watch(() => route.fullPath, () => { mobileNavOpen.value = false })
const heading = computed(() => { const k = SECTION[String(route.name ?? '')]; return k ? t(k) : APP_TITLE.value })
// Also close it if the viewport grows past md (rotation, window resize) — the persistent sidebar
// takes over there and an open drawer would sit on top of it.
const mdQuery = window.matchMedia('(min-width: 768px)')
const onMdChange = (e: MediaQueryListEvent) => { if (e.matches) mobileNavOpen.value = false }
onMounted(() => mdQuery.addEventListener('change', onMdChange))
onUnmounted(() => mdQuery.removeEventListener('change', onMdChange))

const authStore = useAuthStore()
const isMock = authMode === 'mock'
const appVersion = __APP_VERSION__

// Announcements from aquarium (bar above the content) and maintenance started while the app is open
// (maintenance active at start-up is handled in main.ts before anything mounts).
const notices = useNotices({ url: noticesUrl, file: NOTICES_FILE, product: 'orgasm' })

// While a login overlay is up, the routed view must not mount: it would fetch on mount, get a
// 401, latch onto its "not found" error state and never refetch once the user signs in — so a
// deep link opened before login (e.g. a shared /playlists/{id} URL) showed "Playlist not found."
// with no action buttons. Gating <RouterView> on the same condition as the overlays means the
// view mounts (and loads) only once there is a session to load with.
//
// One exception: a Cognito admin with no linked Contributor may still open /admin — otherwise
// nobody could create the first organization to link into (the overlay offers the shortcut).
const loginRequired = computed(() =>
  (isMock && !authStore.isAuthenticated) ||
  (authMode === 'cognito' && !authStore.currentContributor && !(authStore.isAdmin && route.name === 'admin')),
)
</script>

<template>
  <MaintenanceScreen v-if="notices.maintenance.value" :notice="notices.maintenance.value" title="orgasm" />
  <template v-else>
  <!-- Mock login overlay -->
  <MockLoginOverlay v-if="loginRequired && isMock" />

  <!-- Cognito login/link overlay — stays visible through "signed in but not yet linked" -->
  <CognitoLoginOverlay v-if="loginRequired && authMode === 'cognito'" />

  <!-- Organization suspended from the CRM: nothing works until it is resumed (reason is internal) -->
  <div v-if="authStore.orgSuspended" role="alert" class="fixed inset-0 z-[60] flex items-center justify-center bg-background/95 p-4">
    <div class="bg-card rounded-lg border border-destructive/40 p-6 shadow-lg max-w-sm text-center space-y-4">
      <h2 class="text-lg font-semibold">{{ t('suspended.title') }}</h2>
      <p class="text-sm text-muted-foreground">{{ t('suspended.body') }}</p>
      <Button variant="outline" @click="authStore.logout()">{{ t('suspended.logout') }}</Button>
    </div>
  </div>

  <!-- Session expired overlay -->
  <div v-if="authStore.sessionExpired" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div class="bg-card rounded-lg border border-border p-6 shadow-lg max-w-sm text-center space-y-4">
      <h2 class="text-lg font-semibold">{{ t('session.expiredTitle') }}</h2>
      <p class="text-sm text-muted-foreground">{{ t('session.expiredBody') }}</p>
      <Button @click="authStore.reauthenticate()">{{ t('session.login') }}</Button>
    </div>
  </div>

  <div class="flex h-screen bg-background text-foreground">

    <!-- Sidebar (desktop, md and up) -->
    <aside class="hidden md:flex w-56 shrink-0 border-r border-border flex-col">
      <div class="h-14 flex items-center px-4 border-b border-border font-semibold text-sm tracking-wide">
        {{ APP_TITLE }}
      </div>
      <SidebarNav />
    </aside>

    <!-- Nav drawer (mobile, below md) -->
    <Sheet v-model:open="mobileNavOpen">
      <SheetContent side="left" :title="APP_TITLE" class="w-72 max-w-[85vw] gap-0">
        <div class="h-14 flex items-center px-4 pr-12 border-b border-border font-semibold text-sm tracking-wide truncate">
          {{ APP_TITLE }}
        </div>
        <SidebarNav @navigate="mobileNavOpen = false" />
      </SheetContent>
    </Sheet>

    <!-- Content area -->
    <div class="flex flex-1 flex-col overflow-hidden min-w-0">
      <!-- Top bar, always visible: menu button (mobile), current section, announcement bell -->
      <header class="h-14 shrink-0 flex items-center gap-2 px-2 md:px-6 border-b border-border bg-card">
        <Button variant="ghost" size="icon" class="md:hidden" :aria-label="t('app.openMenu')" @click="mobileNavOpen = true">
          <Menu class="h-5 w-5" />
        </Button>
        <span class="min-w-0 flex-1 font-semibold text-sm tracking-wide truncate">{{ heading }}</span>
        <NoticeBell :notices="notices" />
      </header>

      <NoticeBar :notices="notices" />
      <main class="flex-1 overflow-y-auto p-4 md:p-6">
        <LicenseBanner v-if="!loginRequired" />
        <RouterView v-if="!loginRequired" />
      </main>

      <footer class="shrink-0 border-t border-border px-4 md:px-6 py-3 flex items-center justify-between text-xs text-muted-foreground">
        <span>{{ t('app.brand') }}</span>
        <span>v{{ appVersion }}</span>
      </footer>
    </div>

  </div>
  </template>
</template>
