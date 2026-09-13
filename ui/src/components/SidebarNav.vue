<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { Home, ListMusic, Music, Users, Sun, Moon, Monitor, Gamepad2, BarChart3, LogOut, ArrowLeftRight } from 'lucide-vue-next'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/auth'
import { authMode } from '@/authMode'

// Shared between the persistent desktop <aside> and the mobile drawer. `navigate` fires on every
// link click so the drawer host can close itself; the desktop host ignores it.
const emit = defineEmits<{ navigate: [] }>()

const { theme, cycle } = useTheme()
const authStore = useAuthStore()
const isMock = authMode === 'mock'

const linkClass = 'flex items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors'
const activeClass = 'bg-accent text-accent-foreground font-medium'
</script>

<template>
  <nav class="flex-1 px-2 py-3 space-y-0.5 flex flex-col overflow-y-auto">
    <RouterLink
      to="/"
      :class="[linkClass, { [activeClass]: $route.name === 'home' }]"
      @click="emit('navigate')"
    >
      <Home class="h-4 w-4 shrink-0" />
      Home
    </RouterLink>
    <div class="my-1 border-t border-border" />
    <RouterLink to="/playlists" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
      <ListMusic class="h-4 w-4 shrink-0" />
      Playlists
    </RouterLink>
    <RouterLink to="/songs" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
      <Music class="h-4 w-4 shrink-0" />
      Songs
    </RouterLink>
    <RouterLink to="/contributors" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
      <Users class="h-4 w-4 shrink-0" />
      Contributors
    </RouterLink>
    <div class="my-1 border-t border-border" />
    <RouterLink to="/guessing" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
      <Gamepad2 class="h-4 w-4 shrink-0" />
      Guessing
    </RouterLink>
    <RouterLink to="/stats" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
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
      <button :class="['w-full', linkClass]" @click="cycle">
        <Sun v-if="theme === 'light'" class="h-4 w-4 shrink-0" />
        <Moon v-else-if="theme === 'dark'" class="h-4 w-4 shrink-0" />
        <Monitor v-else class="h-4 w-4 shrink-0" />
        <span>{{ theme === 'light' ? 'Light' : theme === 'dark' ? 'Dark' : 'System' }}</span>
      </button>
      <button :class="['w-full', linkClass]" @click="authStore.logout()">
        <ArrowLeftRight v-if="isMock" class="h-4 w-4 shrink-0" />
        <LogOut v-else class="h-4 w-4 shrink-0" />
        <span>{{ isMock ? 'Switch User' : 'Logout' }}</span>
      </button>
    </div>
  </nav>
</template>
