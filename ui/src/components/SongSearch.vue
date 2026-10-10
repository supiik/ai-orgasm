<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loader2, Search } from 'lucide-vue-next'
import { api, type SongSearchHit } from '@/api'
import { cn } from '@/lib/utils'

// Free-text lookup against the external song catalogue (`search-songs` → lib/songSearch).
// Used by every "create a song" form (SongsView's dialog, PlaylistDetailView's nominate
// dialog): choosing a hit emits it so the parent can pre-fill artist/title/album/year while
// keeping the fields editable — the catalogue is a shortcut, never the source of truth.

const props = defineProps<{ inputId?: string; disabled?: boolean }>()
const emit = defineEmits<{ select: [hit: SongSearchHit] }>()
const { t } = useI18n()

const DEBOUNCE_MS = 400
const MIN_CHARS = 2
const INITIAL_LIMIT = 8
const LOAD_MORE_STEP = 10
// Matches the server's own cap (services/songSearch.ts's MAX_LIMIT) — no point asking past it.
const MAX_RESULTS = 25

const query = ref('')
const hits = ref<SongSearchHit[]>([])
const limit = ref(INITIAL_LIMIT)
const loading = ref(false)
const loadingMore = ref(false)
const error = ref<string | null>(null)
const searched = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)
const itemRefs = ref<HTMLButtonElement[]>([])

// Both providers rank their whole candidate set before truncating to `limit` (see
// lib/songSearch/{musicbrainz,spotify}.ts), so re-running the same query with a bigger limit
// deterministically returns a superset — "load more" is just that, never a separate offset/page.
const canLoadMore = computed(() => hits.value.length >= limit.value && limit.value < MAX_RESULTS)

let timer: ReturnType<typeof setTimeout> | undefined
let latest = 0

watch(query, (q) => {
  clearTimeout(timer)
  limit.value = INITIAL_LIMIT
  if (q.trim().length < MIN_CHARS) {
    latest++
    reset()
    return
  }
  timer = setTimeout(() => search(q.trim()), DEBOUNCE_MS)
})

onBeforeUnmount(() => clearTimeout(timer))

function reset() {
  hits.value = []
  error.value = null
  loading.value = false
  loadingMore.value = false
  searched.value = false
}

async function search(q: string, { silent = false } = {}) {
  const seq = ++latest
  if (!silent) loading.value = true
  error.value = null
  try {
    const { data } = await api.songs().search(q, limit.value)
    if (seq !== latest) return // a newer query is in flight; drop this stale answer
    hits.value = data
    searched.value = true
  } catch {
    if (seq !== latest) return
    hits.value = []
    error.value = t('songSearch.failed')
  } finally {
    if (seq === latest) {
      if (!silent) loading.value = false
      loadingMore.value = false
    }
  }
}

function loadMore() {
  limit.value = Math.min(limit.value + LOAD_MORE_STEP, MAX_RESULTS)
  loadingMore.value = true
  search(query.value.trim(), { silent: true })
}

function choose(hit: SongSearchHit) {
  emit('select', hit)
  query.value = ''
  reset()
}

function onInputKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown' && hits.value.length) {
    e.preventDefault()
    itemRefs.value[0]?.focus()
  } else if (e.key === 'Escape' && (hits.value.length || error.value)) {
    e.stopPropagation() // keep the surrounding dialog open; just dismiss the results
    reset()
  } else if (e.key === 'Enter') {
    e.preventDefault() // never submit the surrounding form from the lookup box
    if (hits.value.length === 1) choose(hits.value[0])
  }
}

function onItemKeydown(e: KeyboardEvent, index: number) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    itemRefs.value[index + 1]?.focus()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (index === 0) inputRef.value?.focus()
    else itemRefs.value[index - 1]?.focus()
  } else if (e.key === 'Escape') {
    e.stopPropagation()
    reset()
    inputRef.value?.focus()
  }
}

function subtitle(hit: SongSearchHit) {
  return [hit.album, hit.releaseYear].filter(Boolean).join(' · ')
}
</script>

<template>
  <div class="space-y-1.5" data-testid="song-search">
    <div class="relative">
      <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 opacity-50" />
      <input
        :id="props.inputId"
        ref="inputRef"
        v-model="query"
        type="search"
        autocomplete="off"
        role="combobox"
        :aria-expanded="hits.length > 0"
        aria-autocomplete="list"
        :disabled="props.disabled"
        :placeholder="t('songSearch.placeholder')"
        :class="cn(
          'flex h-9 w-full rounded-md border border-input bg-transparent pl-9 pr-9 py-1 text-sm shadow-sm transition-colors',
          'placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
          'disabled:cursor-not-allowed disabled:opacity-50',
        )"
        @keydown="onInputKeydown"
      />
      <Loader2 v-if="loading" class="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin opacity-60" :aria-label="t('songSearch.searching')" />
    </div>
    <p class="text-xs text-muted-foreground">{{ t('songSearch.hint') }}</p>

    <p v-if="error" class="text-sm text-destructive" role="alert">{{ error }}</p>
    <p v-else-if="searched && !hits.length && !loading" class="text-sm text-muted-foreground">{{ t('songSearch.empty') }}</p>
    <ul
      v-else-if="hits.length"
      role="listbox"
      :aria-label="t('songSearch.results')"
      class="max-h-56 overflow-y-auto rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-sm"
    >
      <li v-for="(hit, i) in hits" :key="`${hit.source}:${hit.externalId}`" role="option" :aria-selected="false">
        <button
          :ref="el => { if (el) itemRefs[i] = el as HTMLButtonElement }"
          type="button"
          class="flex w-full flex-col items-start rounded-sm px-2 py-1.5 text-left text-sm outline-none hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground"
          @click="choose(hit)"
          @keydown="onItemKeydown($event, i)"
        >
          <span class="w-full truncate"><span class="font-medium">{{ hit.artist }}</span> — {{ hit.name }}</span>
          <span v-if="subtitle(hit)" class="w-full truncate text-xs text-muted-foreground">{{ subtitle(hit) }}</span>
        </button>
      </li>
      <li v-if="canLoadMore" role="presentation">
        <button
          type="button"
          class="w-full rounded-sm px-2 py-1.5 text-center text-sm text-muted-foreground outline-none hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground disabled:pointer-events-none disabled:opacity-50"
          :disabled="loadingMore"
          @click="loadMore"
        >
          {{ loadingMore ? t('songSearch.loadingMore') : t('songSearch.loadMore') }}
        </button>
      </li>
    </ul>
  </div>
</template>
