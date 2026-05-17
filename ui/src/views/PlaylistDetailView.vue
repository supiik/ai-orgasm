<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Configuration, PlaylistsApi, type PlaylistResponse } from '@orgasm/backend-client'
import { ArrowLeft, Pencil } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const route = useRoute()
const router = useRouter()
const api = new PlaylistsApi(new Configuration({ basePath: '' }))

const id = Number(route.params.id)
const playlist = ref<PlaylistResponse | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const { data } = await api.findPlaylistById(id)
    playlist.value = data
  } catch {
    error.value = 'Playlist not found.'
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
const form = ref({ name: '', description: '' })
const formError = ref<string | null>(null)
const saving = ref(false)

function openEdit() {
  form.value = { name: playlist.value!.name!, description: playlist.value!.description ?? '' }
  formError.value = null
  dialogOpen.value = true
}

async function submitEdit() {
  if (!form.value.name.trim()) {
    formError.value = 'Name is required.'
    return
  }
  saving.value = true
  formError.value = null
  try {
    const { data } = await api.updatePlaylist(id, {
      name: form.value.name.trim(),
      description: form.value.description.trim() || undefined,
    })
    playlist.value = data
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
        <span v-else>{{ playlist?.name }}</span>
      </h1>
      <Button v-if="playlist" variant="outline" size="sm" class="ml-auto" @click="openEdit">
        <Pencil class="h-4 w-4" />
        Edit
      </Button>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <template v-if="playlist">
      <dl class="divide-y divide-border rounded-md border border-border text-sm">
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">ID</dt>
          <dd>{{ playlist.id }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Name</dt>
          <dd class="font-medium">{{ playlist.name }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Description</dt>
          <dd class="text-muted-foreground">{{ playlist.description ?? '—' }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Created</dt>
          <dd>{{ formatDate(playlist.createdAt!) }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Updated</dt>
          <dd>{{ formatDate(playlist.updatedAt!) }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Version</dt>
          <dd class="text-muted-foreground">{{ playlist.version }}</dd>
        </div>
      </dl>
    </template>

  </div>

  <!-- Edit dialog -->
  <Dialog v-model:open="dialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Edit playlist</DialogTitle>
      </DialogHeader>
      <form class="space-y-4" @submit.prevent="submitEdit">
        <div class="space-y-1.5">
          <Label for="name">Name <span class="text-destructive">*</span></Label>
          <Input id="name" v-model="form.name" placeholder="My playlist" autofocus />
        </div>
        <div class="space-y-1.5">
          <Label for="description">Description</Label>
          <Input id="description" v-model="form.description" placeholder="Optional description" />
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
