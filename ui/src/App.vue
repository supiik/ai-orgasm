<script setup lang="ts">
import { RouterView, RouterLink } from 'vue-router'
import { Home, ListMusic, Music, Users, Sun, Moon, Monitor, Gamepad2, BarChart3, LogOut, ArrowLeftRight } from 'lucide-vue-next'
import { useTheme } from '@/composables/useTheme'
import { useTokenRefresh } from '@/composables/useTokenRefresh'
import { useAuthStore } from '@/stores/auth'
import { authMode } from '@/authMode'
import { Button } from '@/components/ui/button'
import MockLoginOverlay from '@/components/MockLoginOverlay.vue'
import CognitoLoginOverlay from '@/components/CognitoLoginOverlay.vue'

const { theme, cycle } = useTheme()
useTokenRefresh()

const authStore = useAuthStore()
const isMock = authMode === 'mock'
</script>

<template>
  <!-- Mock login overlay -->
  <MockLoginOverlay v-if="isMock && !authStore.isAuthenticated" />

  <!-- Cognito login/link overlay — stays visible through "signed in but not yet linked" -->
  <CognitoLoginOverlay v-if="authMode === 'cognito' && !authStore.currentContributor" />

  <!-- Session expired overlay -->
  <div v-if="authStore.sessionExpired" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
    <div class="bg-card rounded-lg border border-border p-6 shadow-lg max-w-sm text-center space-y-4">
      <h2 class="text-lg font-semibold">Session Expired</h2>
      <p class="text-sm text-muted-foreground">Your session has expired. Please log in again.</p>
      <Button @click="authStore.reauthenticate()">Log in</Button>
    </div>
  </div>

  <div class="flex h-screen bg-background text-foreground">

    <!-- Sidebar -->
    <aside class="w-56 shrink-0 border-r border-border flex flex-col">
      <div class="h-14 flex items-center px-4 border-b border-border font-semibold text-sm tracking-wide">
        ORGAnized Spotify Mediabuilding
      </div>
      <nav class="flex-1 px-2 py-3 space-y-0.5 flex flex-col">
        <RouterLink
          to="/"
          class="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          :class="{ 'bg-accent text-accent-foreground font-medium': $route.name === 'home' }"
        >
          <Home class="h-4 w-4 shrink-0" />
          Home
        </RouterLink>
        <div class="my-1 border-t border-border" />
        <RouterLink
          to="/playlists"
          class="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          active-class="bg-accent text-accent-foreground font-medium"
        >
          <ListMusic class="h-4 w-4 shrink-0" />
          Playlists
        </RouterLink>
        <RouterLink
          to="/songs"
          class="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          active-class="bg-accent text-accent-foreground font-medium"
        >
          <Music class="h-4 w-4 shrink-0" />
          Songs
        </RouterLink>
        <RouterLink
          to="/contributors"
          class="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          active-class="bg-accent text-accent-foreground font-medium"
        >
          <Users class="h-4 w-4 shrink-0" />
          Contributors
        </RouterLink>
        <div class="my-1 border-t border-border" />
        <RouterLink
          to="/guessing"
          class="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          active-class="bg-accent text-accent-foreground font-medium"
        >
          <Gamepad2 class="h-4 w-4 shrink-0" />
          Guessing
        </RouterLink>
        <RouterLink
          to="/stats"
          class="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          active-class="bg-accent text-accent-foreground font-medium"
        >
          <BarChart3 class="h-4 w-4 shrink-0" />
          Stats
        </RouterLink>
        <div class="mt-auto pt-2 border-t border-border space-y-0.5">
          <!-- Current user -->
          <div v-if="authStore.currentContributor" class="flex items-center gap-3 px-3 py-2 text-sm">
            <img
              v-if="authStore.currentContributor.avatarUrl"
              :src="authStore.currentContributor.avatarUrl"
              :alt="authStore.currentContributor.name ?? ''"
              class="h-6 w-6 rounded-full object-cover shrink-0"
            />
            <div v-else class="h-6 w-6 rounded-full bg-muted flex items-center justify-center text-xs font-medium text-muted-foreground shrink-0">
              {{ (authStore.currentContributor.name ?? '?')[0].toUpperCase() }}
            </div>
            <span class="truncate font-medium">{{ authStore.currentContributor.name }}</span>
          </div>
          <button
            class="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
            @click="cycle"
          >
            <Sun v-if="theme === 'light'" class="h-4 w-4 shrink-0" />
            <Moon v-else-if="theme === 'dark'" class="h-4 w-4 shrink-0" />
            <Monitor v-else class="h-4 w-4 shrink-0" />
            <span>{{ theme === 'light' ? 'Light' : theme === 'dark' ? 'Dark' : 'System' }}</span>
          </button>
          <button
            class="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
            @click="authStore.logout()"
          >
            <ArrowLeftRight v-if="isMock" class="h-4 w-4 shrink-0" />
            <LogOut v-else class="h-4 w-4 shrink-0" />
            <span>{{ isMock ? 'Switch User' : 'Logout' }}</span>
          </button>
        </div>
      </nav>
    </aside>

    <!-- Content area -->
    <div class="flex flex-1 flex-col overflow-hidden">
      <main class="flex-1 overflow-y-auto p-6">
        <RouterView />
      </main>

      <footer class="shrink-0 border-t border-border px-6 py-3 flex items-center justify-between text-xs text-muted-foreground">
        <span>Orgasm</span>
        <span>v1.0.0-SNAPSHOT</span>
      </footer>
    </div>

  </div>
</template>
