<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
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

// Mobile nav drawer (< md). Closed on every route change, not just link clicks, so back/forward
// navigation and programmatic pushes (e.g. row clicks in a table) never leave it hanging open.
const route = useRoute()
const mobileNavOpen = ref(false)
watch(() => route.fullPath, () => { mobileNavOpen.value = false })
// Also close it if the viewport grows past md (rotation, window resize) — the persistent sidebar
// takes over there and an open drawer would sit on top of it.
const mdQuery = window.matchMedia('(min-width: 768px)')
const onMdChange = (e: MediaQueryListEvent) => { if (e.matches) mobileNavOpen.value = false }
onMounted(() => mdQuery.addEventListener('change', onMdChange))
onUnmounted(() => mdQuery.removeEventListener('change', onMdChange))

const authStore = useAuthStore()
const isMock = authMode === 'mock'
const appVersion = __APP_VERSION__

// While a login overlay is up, the routed view must not mount: it would fetch on mount, get a
// 401, latch onto its "not found" error state and never refetch once the user signs in — so a
// deep link opened before login (e.g. a shared /playlists/{id} URL) showed "Playlist not found."
// with no action buttons. Gating <RouterView> on the same condition as the overlays means the
// view mounts (and loads) only once there is a session to load with.
const loginRequired = computed(() =>
  (isMock && !authStore.isAuthenticated) || (authMode === 'cognito' && !authStore.currentContributor),
)
</script>

<template>
  <!-- Mock login overlay -->
  <MockLoginOverlay v-if="loginRequired && isMock" />

  <!-- Cognito login/link overlay — stays visible through "signed in but not yet linked" -->
  <CognitoLoginOverlay v-if="loginRequired && authMode === 'cognito'" />

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
      <SheetContent side="left" :title="APP_TITLE">
        <div class="h-14 flex items-center px-4 pr-12 border-b border-border font-semibold text-sm tracking-wide truncate">
          {{ APP_TITLE }}
        </div>
        <SidebarNav @navigate="mobileNavOpen = false" />
      </SheetContent>
    </Sheet>

    <!-- Content area -->
    <div class="flex flex-1 flex-col overflow-hidden min-w-0">
      <!-- Top bar (mobile only) -->
      <header class="md:hidden h-14 shrink-0 flex items-center gap-2 px-2 border-b border-border">
        <Button variant="ghost" size="icon" :aria-label="t('app.openMenu')" @click="mobileNavOpen = true">
          <Menu class="h-5 w-5" />
        </Button>
        <span class="font-semibold text-sm tracking-wide truncate">{{ APP_TITLE }}</span>
      </header>

      <main class="flex-1 overflow-y-auto p-4 md:p-6">
        <RouterView v-if="!loginRequired" />
      </main>

      <footer class="shrink-0 border-t border-border px-4 md:px-6 py-3 flex items-center justify-between text-xs text-muted-foreground">
        <span>{{ t('app.brand') }}</span>
        <span>v{{ appVersion }}</span>
      </footer>
    </div>

  </div>
</template>
