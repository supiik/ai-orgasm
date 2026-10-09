<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api } from '@/api'

// Spotify redirects the browser here after the OAuth consent screen (see
// PlaylistDetailView.vue's connectSpotify()). This is the one stable URL registered with the
// Spotify app — the code/state round trip is then forwarded to whichever backend is active via
// api.spotify().handleCallback(), same backend-agnostic pattern as every other api.* call.

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const error = ref<string | null>(null)

onMounted(async () => {
  const code = route.query.code as string | undefined
  const state = route.query.state as string | undefined
  if (!code || !state) {
    error.value = t('spotifyCallback.failed')
    return
  }
  try {
    const { data } = await api.spotify().handleCallback(code, state)
    const target = data.playlistId ? `/playlists/${data.playlistId}` : '/playlists'
    router.replace({ path: target, query: data.playlistId ? { spotifyConnected: '1' } : {} })
  } catch {
    error.value = t('spotifyCallback.failed')
  }
})
</script>

<template>
  <div class="max-w-md mx-auto mt-16 text-center space-y-4">
    <template v-if="error">
      <p class="text-sm text-destructive">{{ error }}</p>
      <RouterLink to="/playlists" class="text-sm hover:underline underline-offset-2">{{ t('spotifyCallback.backToPlaylists') }}</RouterLink>
    </template>
    <p v-else class="text-sm text-muted-foreground">{{ t('spotifyCallback.connecting') }}</p>
  </div>
</template>
