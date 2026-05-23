<script setup lang="ts">
import { RouterView, RouterLink } from 'vue-router'
import { Home, ListMusic, Music, Users, Sun, Moon, Monitor, Gamepad2, BarChart3, LogOut } from 'lucide-vue-next'
import { useTheme } from '@/composables/useTheme'

const { theme, cycle } = useTheme()

const isMock = import.meta.env.VITE_MOCK === 'true'

async function logout() {
  const keycloak = (await import('./keycloak')).default
  await keycloak.logout({ redirectUri: window.location.origin })
}
</script>

<template>
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
        <div class="mt-auto pt-2 border-t border-border">
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
            v-if="!isMock"
            class="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
            @click="logout"
          >
            <LogOut class="h-4 w-4 shrink-0" />
            <span>Logout</span>
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
