<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { type SongResponse } from '@orgasm/backend-client'
import { api } from '@/api'
import { Music } from 'lucide-vue-next'

const router = useRouter()
const songs = ref<SongResponse[]>([])
const artworkMap = ref<Record<string, string>>({})
const loading = ref(true)

async function fetchArtwork(artist: string, song: string): Promise<string | null> {
  try {
    const query = encodeURIComponent(`${artist} ${song}`)
    const res = await fetch(`https://itunes.apple.com/search?term=${query}&media=music&limit=1`)
    const data = await res.json()
    const url = data.results?.[0]?.artworkUrl100
    return url ? url.replace('100x100', '300x300') : null
  } catch {
    return null
  }
}

onMounted(async () => {
  try {
    const { data } = await api.songs().list(0, 10, 'createdAt,desc')
    songs.value = data.content ?? []
    const lookups = songs.value.map(async (s) => {
      const url = await fetchArtwork(s.artist!, s.name!)
      if (url) artworkMap.value[s.id!] = url
    })
    await Promise.all(lookups)
  } catch {
    // silent
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Recent Songs</h1>

    <div v-if="loading" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
      <div v-for="i in 10" :key="i" class="space-y-2">
        <div class="aspect-square rounded-md bg-muted animate-pulse" />
        <div class="h-4 w-3/4 rounded bg-muted animate-pulse" />
        <div class="h-3 w-1/2 rounded bg-muted animate-pulse" />
      </div>
    </div>

    <div v-else-if="songs.length" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
      <div
        v-for="song in songs"
        :key="song.id"
        class="group cursor-pointer space-y-2"
        @click="router.push(`/songs/${song.id}`)"
      >
        <div class="aspect-square rounded-md overflow-hidden bg-muted">
          <img
            v-if="artworkMap[song.id!]"
            :src="artworkMap[song.id!]"
            :alt="`${song.artist} – ${song.name}`"
            class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
          />
          <div v-else class="w-full h-full flex items-center justify-center text-muted-foreground">
            <Music class="h-10 w-10" />
          </div>
        </div>
        <div class="min-w-0">
          <div class="font-medium truncate group-hover:underline underline-offset-2">{{ song.name }}</div>
          <div class="text-sm text-muted-foreground truncate">{{ song.artist }}</div>
        </div>
      </div>
    </div>

    <p v-else class="text-muted-foreground">No songs yet.</p>
  </div>
</template>
