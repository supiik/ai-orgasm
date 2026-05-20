<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type SongResponse } from '@etactics/proxima-sal-client'
import { api } from '@/api'
import { ArrowLeft, Pencil } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const route = useRoute()
const router = useRouter()

const id = route.params.id as string
const song = ref<SongResponse | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const { data } = await api.songs().get(id)
    song.value = data
  } catch {
    error.value = 'Song not found.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function formatDate(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

// ── Edit dialog ───────────────────────────────────────────────────────────────

const dialogOpen = ref(false)
const form = ref({ artist: '', name: '', album: '', releaseYear: '' })
const formError = ref<string | null>(null)
const saving = ref(false)

function openEdit() {
  form.value = {
    artist: song.value!.artist ?? '',
    name: song.value!.name ?? '',
    album: song.value!.album ?? '',
    releaseYear: song.value!.releaseYear != null ? String(song.value!.releaseYear) : '',
  }
  formError.value = null
  dialogOpen.value = true
}

async function submitEdit() {
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
    const releaseYear = form.value.releaseYear.trim() ? Number(form.value.releaseYear) : undefined
    const { data } = await api.songs().update(id, {
      artist: form.value.artist.trim(),
      name: form.value.name.trim(),
      album: form.value.album.trim() || undefined,
      releaseYear,
    })
    song.value = data
    dialogOpen.value = false
  } catch {
    formError.value = 'Failed to save. Please try again.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-6 max-w-2xl">

    <div class="flex items-center gap-3">
      <Button variant="ghost" size="icon" @click="router.back()">
        <ArrowLeft class="h-4 w-4" />
      </Button>
      <h1 class="text-2xl font-semibold">
        <span v-if="loading" class="inline-block w-48 h-7 rounded bg-muted animate-pulse" />
        <span v-else>{{ song?.name }}</span>
      </h1>
      <Button v-if="song" variant="outline" size="sm" class="ml-auto" @click="openEdit">
        <Pencil class="h-4 w-4" />
        Edit
      </Button>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <template v-if="song">
      <dl class="divide-y divide-border rounded-md border border-border text-sm overflow-hidden [&>div:nth-child(even)]:bg-muted/40">
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">ID</dt>
          <dd>{{ song.id }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Artist</dt>
          <dd class="font-medium">{{ song.artist }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Name</dt>
          <dd class="font-medium">{{ song.name }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Album</dt>
          <dd class="text-muted-foreground">{{ song.album ?? '—' }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Release year</dt>
          <dd class="text-muted-foreground">{{ song.releaseYear ?? '—' }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Created</dt>
          <dd>{{ formatDate(song.createdAt!) }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Updated</dt>
          <dd>{{ formatDate(song.updatedAt!) }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Version</dt>
          <dd class="text-muted-foreground">{{ song.version }}</dd>
        </div>
      </dl>
    </template>

  </div>

  <!-- Edit dialog -->
  <Dialog v-model:open="dialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Edit song</DialogTitle>
      </DialogHeader>
      <form class="space-y-4" @submit.prevent="submitEdit">
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
        <Button :disabled="saving" @click="submitEdit">{{ saving ? 'Saving…' : 'Save' }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
