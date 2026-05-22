<script setup lang="ts">
import { computed } from 'vue'
import { Music } from 'lucide-vue-next'

const props = defineProps<{ url: string }>()

const SERVICES = [
  { name: 'Spotify',      match: 'spotify.com',       cls: 'text-green-700 bg-green-100 hover:bg-green-200 dark:text-green-400 dark:bg-green-950 dark:hover:bg-green-900' },
  { name: 'YouTube',      match: 'youtube.com',        cls: 'text-red-600 bg-red-100 hover:bg-red-200 dark:text-red-400 dark:bg-red-950 dark:hover:bg-red-900' },
  { name: 'YouTube',      match: 'youtu.be',           cls: 'text-red-600 bg-red-100 hover:bg-red-200 dark:text-red-400 dark:bg-red-950 dark:hover:bg-red-900' },
  { name: 'Deezer',       match: 'deezer.com',         cls: 'text-purple-700 bg-purple-100 hover:bg-purple-200 dark:text-purple-400 dark:bg-purple-950 dark:hover:bg-purple-900' },
  { name: 'Apple Music',  match: 'music.apple.com',    cls: 'text-pink-700 bg-pink-100 hover:bg-pink-200 dark:text-pink-400 dark:bg-pink-950 dark:hover:bg-pink-900' },
  { name: 'SoundCloud',   match: 'soundcloud.com',     cls: 'text-orange-700 bg-orange-100 hover:bg-orange-200 dark:text-orange-400 dark:bg-orange-950 dark:hover:bg-orange-900' },
  { name: 'Tidal',        match: 'tidal.com',          cls: 'text-foreground bg-muted hover:bg-muted/70' },
  { name: 'Amazon Music', match: 'music.amazon',       cls: 'text-sky-700 bg-sky-100 hover:bg-sky-200 dark:text-sky-400 dark:bg-sky-950 dark:hover:bg-sky-900' },
]

const service = computed(() => {
  try {
    const host = new URL(props.url).hostname.replace(/^www\./, '')
    return SERVICES.find(s => host.includes(s.match))
      ?? { name: 'Link', cls: 'text-muted-foreground bg-muted hover:bg-muted/70' }
  } catch {
    return { name: 'Link', cls: 'text-muted-foreground bg-muted hover:bg-muted/70' }
  }
})
</script>

<template>
  <a
    :href="url"
    target="_blank"
    rel="noopener noreferrer"
    :class="['inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs font-medium transition-colors', service.cls]"
    @click.stop
  >
    <Music class="h-3 w-3 shrink-0" />
    {{ service.name }}
  </a>
</template>
