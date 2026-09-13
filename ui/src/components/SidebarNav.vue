<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Home, ListMusic, Music, Users, Sun, Moon, Monitor, Gamepad2, BarChart3, LogOut, ArrowLeftRight, Languages, ShieldCheck } from 'lucide-vue-next'
import { useTheme } from '@/composables/useTheme'
import { useLocale } from '@/composables/useLocale'
import { useAuthStore } from '@/stores/auth'
import { authMode } from '@/authMode'
import { Select, SelectItem } from '@/components/ui/select'

// Shared between the persistent desktop <aside> and the mobile drawer. `navigate` fires on every
// link click so the drawer host can close itself; the desktop host ignores it.
const emit = defineEmits<{ navigate: [] }>()

const { t } = useI18n()
const { theme, cycle } = useTheme()
const { locale, supportedLocales } = useLocale()
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
      {{ t('nav.home') }}
    </RouterLink>
    <div class="my-1 border-t border-border" />
    <RouterLink to="/playlists" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
      <ListMusic class="h-4 w-4 shrink-0" />
      {{ t('nav.playlists') }}
    </RouterLink>
    <RouterLink to="/songs" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
      <Music class="h-4 w-4 shrink-0" />
      {{ t('nav.songs') }}
    </RouterLink>
    <RouterLink to="/contributors" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
      <Users class="h-4 w-4 shrink-0" />
      {{ t('nav.contributors') }}
    </RouterLink>
    <div class="my-1 border-t border-border" />
    <RouterLink to="/guessing" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
      <Gamepad2 class="h-4 w-4 shrink-0" />
      {{ t('nav.guessing') }}
    </RouterLink>
    <RouterLink to="/stats" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
      <BarChart3 class="h-4 w-4 shrink-0" />
      {{ t('nav.stats') }}
    </RouterLink>
    <template v-if="authStore.isAdmin">
      <div class="my-1 border-t border-border" />
      <RouterLink to="/admin" :class="linkClass" :active-class="activeClass" @click="emit('navigate')">
        <ShieldCheck class="h-4 w-4 shrink-0" />
        {{ t('nav.admin') }}
      </RouterLink>
    </template>
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
      <!-- Language -->
      <div :class="[linkClass, 'hover:bg-transparent hover:text-muted-foreground']">
        <Languages class="h-4 w-4 shrink-0" />
        <Select
          v-model="locale"
          :aria-label="t('nav.language')"
          class="h-7 flex-1 border-0 bg-transparent px-0 shadow-none focus:ring-0 text-muted-foreground hover:text-accent-foreground"
        >
          <SelectItem v-for="l in supportedLocales" :key="l" :value="l">{{ t(`locale.${l}`) }}</SelectItem>
        </Select>
      </div>
      <button :class="['w-full', linkClass]" @click="cycle">
        <Sun v-if="theme === 'light'" class="h-4 w-4 shrink-0" />
        <Moon v-else-if="theme === 'dark'" class="h-4 w-4 shrink-0" />
        <Monitor v-else class="h-4 w-4 shrink-0" />
        <span>{{ t(`theme.${theme}`) }}</span>
      </button>
      <button :class="['w-full', linkClass]" @click="authStore.logout()">
        <ArrowLeftRight v-if="isMock" class="h-4 w-4 shrink-0" />
        <LogOut v-else class="h-4 w-4 shrink-0" />
        <span>{{ isMock ? t('nav.switchUser') : t('nav.logout') }}</span>
      </button>
    </div>
  </nav>
</template>
