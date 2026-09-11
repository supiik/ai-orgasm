<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { type SongPage, type SongResponse } from '@orgasm/backend-client'
import { api } from '@/api'
import { ChevronLeft, ChevronRight, Plus, Pencil } from 'lucide-vue-next'
import NameFilter from '@/components/NameFilter.vue'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const router = useRouter()
// ── Table ────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 10
const page = ref(0)
const nameFilter = ref('')
const data = ref<SongPage | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function fetchPage(p: number) {
  loading.value = true
  error.value = null
  try {
    const { data: body } = await api.songs().list(p, PAGE_SIZE, 'id', nameFilter.value || undefined)
    data.value = body
  } catch {
    error.value = 'Failed to load songs.'
  } finally {
    loading.value = false
  }
}

watch(page, fetchPage, { immediate: true })
watch(nameFilter, () => { page.value === 0 ? fetchPage(0) : (page.value = 0) })

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { dateStyle: 'medium' })
}

// ── Create / Edit dialog ──────────────────────────────────────────────────────

type DialogMode = 'create' | 'edit'

const dialogOpen = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<string | null>(null)
const form = ref({ artist: '', name: '', album: '', releaseYear: '' })
const formError = ref<string | null>(null)
const saving = ref(false)

const dialogTitle = computed(() => dialogMode.value === 'create' ? 'New song' : 'Edit song')
const submitLabel = computed(() => {
  if (saving.value) return dialogMode.value === 'create' ? 'Creating…' : 'Saving…'
  return dialogMode.value === 'create' ? 'Create' : 'Save'
})

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.value = { artist: '', name: '', album: '', releaseYear: '' }
  formError.value = null
  dialogOpen.value = true
}

function openEdit(song: SongResponse) {
  dialogMode.value = 'edit'
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

async function submitForm() {
  if (!form.value.artist.trim()) {
    formError.value = 'Artist is required.'
    return
  }
  if (!form.value.name.trim()) {
    formError.value = 'Name is required.'
    return
  }
  saving.value = true
  formError.value = null
  try {
    // Vue's v-model on <input type="number"> casts the bound value to a JS number once
    // non-empty, despite form.releaseYear being typed as string — so it can't be assumed to
    // have .trim(). String(...) normalizes both cases before the emptiness check.
    const releaseYearRaw = String(form.value.releaseYear).trim()
    const releaseYear = releaseYearRaw ? Number(releaseYearRaw) : undefined
    const payload = {
      artist: form.value.artist.trim(),
      name: form.value.name.trim(),
      album: form.value.album.trim() || undefined,
      releaseYear,
    }
    if (dialogMode.value === 'create') {
      await api.songs().create(payload)
      page.value === 0 ? fetchPage(0) : (page.value = 0)
    } else {
      await api.songs().update(editingId.value!, payload)
      fetchPage(page.value)
    }
    dialogOpen.value = false
  } catch {
    formError.value = `Failed to ${dialogMode.value === 'create' ? 'create' : 'save'} song. Please try again.`
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-4">

    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">Songs</h1>
      <div class="flex items-center gap-2">
        <NameFilter v-model="nameFilter" placeholder="Filter by name…" />
        <Button @click="openCreate">
          <Plus class="h-4 w-4" />
          New song
        </Button>
      </div>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <div class="rounded-md border border-border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-16">ID</TableHead>
            <TableHead>Artist</TableHead>
            <TableHead>Name</TableHead>
            <TableHead>Album</TableHead>
            <TableHead class="w-28">Year</TableHead>
            <TableHead class="w-36">Created</TableHead>
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
                No songs found.
              </TableCell>
            </TableRow>
          </template>
        </TableBody>
      </Table>
    </div>

    <!-- Pagination -->
    <div v-if="data && data.totalPages! > 1" class="flex items-center justify-between text-sm text-muted-foreground">
      <span>
        {{ data.totalElements }} song{{ data.totalElements !== 1 ? 's' : '' }} —
        page {{ data.number! + 1 }} of {{ data.totalPages }}
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

  <!-- Create / Edit dialog -->
  <Dialog v-model:open="dialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ dialogTitle }}</DialogTitle>
      </DialogHeader>

      <form class="space-y-4" @submit.prevent="submitForm">
        <div class="space-y-1.5">
          <Label for="artist">Artist <span class="text-destructive">*</span></Label>
          <Input id="artist" v-model="form.artist" placeholder="Artist name" autofocus />
        </div>
        <div class="space-y-1.5">
          <Label for="name">Name <span class="text-destructive">*</span></Label>
          <Input id="name" v-model="form.name" placeholder="Song title" />
        </div>
        <div class="space-y-1.5">
          <Label for="album">Album</Label>
          <Input id="album" v-model="form.album" placeholder="Album name" />
        </div>
        <div class="space-y-1.5">
          <Label for="releaseYear">Release year</Label>
          <Input id="releaseYear" v-model="form.releaseYear" type="number" placeholder="e.g. 1993" />
        </div>
        <p v-if="formError" class="text-sm text-destructive">{{ formError }}</p>
      </form>

      <DialogFooter>
        <Button variant="outline" :disabled="saving" @click="dialogOpen = false">Cancel</Button>
        <Button :disabled="saving" @click="submitForm">{{ submitLabel }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
