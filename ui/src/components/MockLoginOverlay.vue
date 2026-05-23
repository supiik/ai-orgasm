<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { ContributorResponse } from '@orgasm/backend-client'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const contributors = ref<ContributorResponse[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const { data } = await api.contributors().list(0, 100)
    contributors.value = data.content ?? []
  } finally {
    loading.value = false
  }
})

function login(contributor: ContributorResponse) {
  authStore.mockLogin(contributor)
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-background">
    <div class="w-full max-w-lg px-6 space-y-6 text-center">
      <div class="space-y-2">
        <h1 class="text-xl font-semibold tracking-wide">ORGAnized Spotify Mediabuilding</h1>
        <p class="text-sm text-muted-foreground">Select your identity to continue</p>
      </div>

      <div v-if="loading" class="space-y-3">
        <div v-for="i in 3" :key="i" class="h-16 rounded-lg bg-muted animate-pulse" />
      </div>

      <div v-else class="space-y-2">
        <button
          v-for="c in contributors"
          :key="c.id"
          class="flex w-full items-center gap-4 rounded-lg border border-border px-4 py-3 text-left transition-colors hover:bg-accent hover:text-accent-foreground"
          @click="login(c)"
        >
          <img
            v-if="c.avatarUrl"
            :src="c.avatarUrl"
            :alt="c.name"
            class="h-10 w-10 rounded-full object-cover shrink-0"
          />
          <div
            v-else
            class="h-10 w-10 rounded-full bg-muted flex items-center justify-center text-sm font-medium text-muted-foreground shrink-0"
          >
            {{ (c.name ?? '?')[0].toUpperCase() }}
          </div>
          <div class="min-w-0">
            <div class="font-medium truncate">{{ c.name }}</div>
            <div v-if="c.email" class="text-xs text-muted-foreground truncate">{{ c.email }}</div>
          </div>
        </button>
      </div>
    </div>
  </div>
</template>
