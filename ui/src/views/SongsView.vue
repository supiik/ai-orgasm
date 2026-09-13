<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { type SongPage, type SongResponse } from '@orgasm/backend-client'
import { api, type SongSearchHit } from '@/api'
import { parseReleaseYear } from '@/lib/releaseYear'
import { ChevronLeft, ChevronRight, Pencil } from 'lucide-vue-next'
import NameFilter from '@/components/NameFilter.vue'
import SongSearch from '@/components/SongSearch.vue'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const router = useRouter()
const { t, d } = useI18n()
// ── Table ────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 10
const page = ref(0)
const nameFilter = ref('')
const data = ref<SongPage | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

// Monotonic request counter: a slow response for an earlier filter must not overwrite the
// result of a later one (typing "c", then "creep" — the "c" request may finish last).
let requestSeq = 0

async function fetchPage(p: number) {
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const { data: body } = await api.songs().list(p, PAGE_SIZE, 'id', nameFilter.value || undefined)
    if (seq !== requestSeq) return
    data.value = body
  } catch {
    if (seq !== requestSeq) return
    error.value = t('songs.loadFailed')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

watch(page, fetchPage, { immediate: true })
watch(nameFilter, () => { page.value === 0 ? fetchPage(0) : (page.value = 0) })

function formatDate(iso: string) {
  return d(new Date(iso), 'date')
}

// ── Edit dialog ──────────────────────────────────────────────────────────────
// Deliberately edit-only: a song only exists as somebody's nomination, so it is created from the
// "Nominate a song" dialog on an open playlist (PlaylistDetailView), never as a detached record.

const dialogOpen = ref(false)
const editingId = ref<string | null>(null)
const form = ref({ artist: '', name: '', album: '', releaseYear: '' })
const formError = ref<string | null>(null)
const saving = ref(false)

const submitLabel = computed(() => saving.value ? t('common.saving') : t('common.save'))

function openEdit(song: SongResponse) {
  editingId.value = song.id!
  form.value = {
    artist: song.artist ?? '',
    name: song.name ?? '',
    album: song.album ?? '',
    releaseYear: song.releaseYear != null ? String(song.releaseYear) : '',
  }
  formError.value = null
  dialogOpen.value = true
}

function applySearchHit(hit: SongSearchHit) {
  form.value = {
    artist: hit.artist,
    name: hit.name,
    album: hit.album ?? '',
    releaseYear: hit.releaseYear != null ? String(hit.releaseYear) : '',
  }
  formError.value = null
}

async function submitForm() {
  if (!form.value.artist.trim()) {
    formError.value = t('common.artistRequired')
    return
  }
  if (!form.value.name.trim()) {
    formError.value = t('common.nameRequired')
    return
  }
  saving.value = true
  formError.value = null
  try {
    const releaseYear = parseReleaseYear(form.value.releaseYear)
    const payload = {
      artist: form.value.artist.trim(),
      name: form.value.name.trim(),
      album: form.value.album.trim() || undefined,
      releaseYear,
    }
    await api.songs().update(editingId.value!, payload)
    fetchPage(page.value)
    dialogOpen.value = false
  } catch {
    formError.value = t('songs.saveFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-4">

    <div class="flex items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold">{{ t('songs.title') }}</h1>
        <p class="text-sm text-muted-foreground">
          {{ t('songs.addViaNomination') }}
          <RouterLink to="/playlists" class="underline underline-offset-2 hover:text-foreground">{{ t('nav.playlists') }}</RouterLink>
        </p>
      </div>
      <NameFilter v-model="nameFilter" />
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <div class="rounded-md border border-border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-16">{{ t('common.id') }}</TableHead>
            <TableHead>{{ t('common.artist') }}</TableHead>
            <TableHead>{{ t('common.name') }}</TableHead>
            <TableHead>{{ t('common.album') }}</TableHead>
            <TableHead class="w-28">{{ t('common.year') }}</TableHead>
            <TableHead class="w-36">{{ t('common.created') }}</TableHead>
            <TableHead class="w-12" />
          </TableRow>
        </TableHeader>
        <TableBody>
          <template v-if="loading">
            <TableRow v-for="i in PAGE_SIZE" :key="i">
              <TableCell colspan="7">
                <div class="h-4 rounded bg-muted animate-pulse" />
              </TableCell>
            </TableRow>
          </template>
          <template v-else-if="data && data.content?.length">
            <TableRow
              v-for="song in data.content"
              :key="song.id"
              class="cursor-pointer"
              @click="router.push({ name: 'song-detail', params: { id: song.id } })"
            >
              <TableCell class="text-muted-foreground">{{ song.id }}</TableCell>
              <TableCell class="font-medium">{{ song.artist }}</TableCell>
              <TableCell>{{ song.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ song.album ?? '—' }}</TableCell>
              <TableCell class="text-muted-foreground">{{ song.releaseYear ?? '—' }}</TableCell>
              <TableCell class="text-muted-foreground">{{ formatDate(song.createdAt!) }}</TableCell>
              <TableCell>
                <Button variant="ghost" size="icon" @click.stop="openEdit(song)">
                  <Pencil class="h-4 w-4" />
                </Button>
              </TableCell>
            </TableRow>
          </template>
          <template v-else>
            <TableRow>
              <TableCell colspan="7" class="text-center text-muted-foreground py-10">
                {{ t('songs.empty') }}
              </TableCell>
            </TableRow>
          </template>
        </TableBody>
      </Table>
    </div>

    <!-- Pagination -->
    <div v-if="data && data.totalPages! > 1" class="flex items-center justify-between text-sm text-muted-foreground">
      <span>
        {{ t('songs.count', data.totalElements!) }} —
        {{ t('common.pageOf', { page: data.number! + 1, total: data.totalPages }) }}
      </span>
      <div class="flex gap-1">
        <Button variant="outline" size="icon" :disabled="page === 0" @click="page--">
          <ChevronLeft class="h-4 w-4" />
        </Button>
        <Button variant="outline" size="icon" :disabled="page >= data.totalPages! - 1" @click="page++">
          <ChevronRight class="h-4 w-4" />
        </Button>
      </div>
    </div>

  </div>

  <!-- Edit dialog -->
  <Dialog v-model:open="dialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ t('songs.editTitle') }}</DialogTitle>
      </DialogHeader>

      <form class="space-y-4" @submit.prevent="submitForm">
        <div v-if="dialogMode === 'create'" class="space-y-1.5 border-b border-border pb-4">
          <Label for="song-search">{{ t('songSearch.label') }}</Label>
          <SongSearch input-id="song-search" @select="applySearchHit" />
        </div>
        <div class="space-y-1.5">
          <Label for="artist">{{ t('common.artist') }} <span class="text-destructive">*</span></Label>
          <Input id="artist" v-model="form.artist" :placeholder="t('fields.artistName')" :autofocus="dialogMode === 'edit'" />
        </div>
        <div class="space-y-1.5">
          <Label for="name">{{ t('common.name') }} <span class="text-destructive">*</span></Label>
          <Input id="name" v-model="form.name" :placeholder="t('fields.songTitle')" />
        </div>
        <div class="space-y-1.5">
          <Label for="album">{{ t('common.album') }}</Label>
          <Input id="album" v-model="form.album" :placeholder="t('fields.albumName')" />
        </div>
        <div class="space-y-1.5">
          <Label for="releaseYear">{{ t('common.releaseYear') }}</Label>
          <Input id="releaseYear" v-model="form.releaseYear" type="number" :placeholder="t('fields.yearExample')" />
        </div>
        <p v-if="formError" class="text-sm text-destructive">{{ formError }}</p>
      </form>

      <DialogFooter>
        <Button variant="outline" :disabled="saving" @click="dialogOpen = false">{{ t('common.cancel') }}</Button>
        <Button :disabled="saving" @click="submitForm">{{ submitLabel }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
