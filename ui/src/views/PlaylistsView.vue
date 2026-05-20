<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { type PlaylistPage, type PlaylistResponse as BasePlaylistResponse } from '@etactics/proxima-sal-client'

type PlaylistResponse = BasePlaylistResponse & {
  leadContributorName?: string | null
  leadContributorAvatarUrl?: string | null
}
type EnrichedPlaylistPage = Omit<PlaylistPage, 'content'> & { content?: PlaylistResponse[] }
import { api } from '@/api'
import { ChevronLeft, ChevronRight, Plus, Pencil } from 'lucide-vue-next'
import NameFilter from '@/components/NameFilter.vue'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PlaylistStatusBadge from '@/components/PlaylistStatusBadge.vue'

const router = useRouter()
// ── Table ────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 10
const page = ref(0)
const nameFilter = ref('')
const data = ref<EnrichedPlaylistPage | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function fetchPage(p: number) {
  loading.value = true
  error.value = null
  try {
    const { data: body } = await api.playlists().list(p, PAGE_SIZE, 'id', nameFilter.value || undefined)
    data.value = body as EnrichedPlaylistPage
  } catch {
    error.value = 'Failed to load playlists.'
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
const form = ref({ name: '', description: '' })
const formError = ref<string | null>(null)
const saving = ref(false)

const dialogTitle = computed(() => dialogMode.value === 'create' ? 'New playlist' : 'Edit playlist')
const submitLabel = computed(() => {
  if (saving.value) return dialogMode.value === 'create' ? 'Creating…' : 'Saving…'
  return dialogMode.value === 'create' ? 'Create' : 'Save'
})

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.value = { name: '', description: '' }
  formError.value = null
  dialogOpen.value = true
}

function openEdit(playlist: PlaylistResponse) {
  dialogMode.value = 'edit'
  editingId.value = playlist.id!
  form.value = { name: playlist.name!, description: playlist.description ?? '' }
  formError.value = null
  dialogOpen.value = true
}

async function submitForm() {
  if (!form.value.name.trim()) {
    formError.value = 'Name is required.'
    return
  }
  saving.value = true
  formError.value = null
  try {
    const payload = {
      name: form.value.name.trim(),
      description: form.value.description.trim() || undefined,
    }
    if (dialogMode.value === 'create') {
      await api.playlists().create(payload)
      page.value === 0 ? fetchPage(0) : (page.value = 0)
    } else {
      await api.playlists().update(editingId.value!, payload)
      fetchPage(page.value)
    }
    dialogOpen.value = false
  } catch {
    formError.value = `Failed to ${dialogMode.value === 'create' ? 'create' : 'save'} playlist. Please try again.`
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-4">

    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">Playlists</h1>
      <div class="flex items-center gap-2">
        <NameFilter v-model="nameFilter" placeholder="Filter by name…" />
        <Button @click="openCreate">
          <Plus class="h-4 w-4" />
          New playlist
        </Button>
      </div>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <div class="rounded-md border border-border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Description</TableHead>
            <TableHead class="w-36">Status</TableHead>
            <TableHead class="w-44">Lead</TableHead>
            <TableHead class="w-36">Created</TableHead>
            <TableHead class="w-36">Updated</TableHead>
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
              v-for="playlist in data.content"
              :key="playlist.id"
              class="cursor-pointer"
              @click="router.push({ name: 'playlist-detail', params: { id: playlist.id } })"
            >
              <TableCell class="font-medium">{{ playlist.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ playlist.description ?? '—' }}</TableCell>
              <TableCell><PlaylistStatusBadge v-if="playlist.status" :status="playlist.status" /></TableCell>
              <TableCell>
                <div v-if="playlist.leadContributorId" class="flex items-center gap-2">
                  <img v-if="playlist.leadContributorAvatarUrl" :src="playlist.leadContributorAvatarUrl" :alt="playlist.leadContributorName ?? ''" class="w-6 h-6 rounded-full object-cover shrink-0" />
                  <div v-else class="w-6 h-6 rounded-full bg-muted shrink-0" />
                  <span class="text-sm truncate">{{ playlist.leadContributorName ?? playlist.leadContributorId }}</span>
                </div>
                <span v-else class="text-muted-foreground">—</span>
              </TableCell>
              <TableCell class="text-muted-foreground">{{ formatDate(playlist.createdAt!) }}</TableCell>
              <TableCell class="text-muted-foreground">{{ formatDate(playlist.updatedAt!) }}</TableCell>
              <TableCell>
                <Button variant="ghost" size="icon" @click.stop="openEdit(playlist)">
                  <Pencil class="h-4 w-4" />
                </Button>
              </TableCell>
            </TableRow>
          </template>
          <template v-else>
            <TableRow>
              <TableCell colspan="7" class="text-center text-muted-foreground py-10">
                No playlists found.
              </TableCell>
            </TableRow>
          </template>
        </TableBody>
      </Table>
    </div>

    <!-- Pagination -->
    <div v-if="data && data.totalPages! > 1" class="flex items-center justify-between text-sm text-muted-foreground">
      <span>
        {{ data.totalElements }} playlist{{ data.totalElements !== 1 ? 's' : '' }} —
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
        <Button :disabled="saving" @click="submitForm">{{ submitLabel }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
