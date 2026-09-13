<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { type PlaylistPage, type PlaylistResponse as BasePlaylistResponse } from '@orgasm/backend-client'

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
import { Select, SelectItem } from '@/components/ui/select'
import PlaylistStatusBadge from '@/components/PlaylistStatusBadge.vue'

const router = useRouter()
const { t, d } = useI18n()
// ── Table ────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 10
const page = ref(0)
const nameFilter = ref('')
const data = ref<EnrichedPlaylistPage | null>(null)
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
    const { data: body } = await api.playlists().list(p, PAGE_SIZE, 'id', nameFilter.value || undefined)
    if (seq !== requestSeq) return
    data.value = body as EnrichedPlaylistPage
  } catch {
    if (seq !== requestSeq) return
    error.value = t('playlists.loadFailed')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

watch(page, fetchPage, { immediate: true })
watch(nameFilter, () => { page.value === 0 ? fetchPage(0) : (page.value = 0) })

function formatDate(iso: string) {
  return d(new Date(iso), 'date')
}

// ── Create / Edit dialog ──────────────────────────────────────────────────────

type DialogMode = 'create' | 'edit'

const dialogOpen = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<string | null>(null)
const RATING_TYPES = ['LINEAR', 'FIBONACCI', 'BEST_SONG'] as const

const form = ref({ name: '', description: '', ratingType: '' })
const formError = ref<string | null>(null)
const saving = ref(false)

const dialogTitle = computed(() => dialogMode.value === 'create' ? t('playlists.new') : t('playlists.editTitle'))
const submitLabel = computed(() => {
  if (saving.value) return dialogMode.value === 'create' ? t('common.creating') : t('common.saving')
  return dialogMode.value === 'create' ? t('common.create') : t('common.save')
})

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.value = { name: '', description: '', ratingType: '' }
  formError.value = null
  dialogOpen.value = true
}

function openEdit(playlist: PlaylistResponse) {
  dialogMode.value = 'edit'
  editingId.value = playlist.id!
  form.value = { name: playlist.name!, description: playlist.description ?? '', ratingType: '' }
  formError.value = null
  dialogOpen.value = true
}

async function submitForm() {
  if (!form.value.name.trim()) {
    formError.value = t('common.nameRequired')
    return
  }
  saving.value = true
  formError.value = null
  try {
    const payload = {
      name: form.value.name.trim(),
      description: form.value.description.trim() || undefined,
      ...(dialogMode.value === 'create' && form.value.ratingType
        ? { ratingType: form.value.ratingType }
        : {}),
    }
    if (dialogMode.value === 'create') {
      await api.playlists().create(payload as any)
      page.value === 0 ? fetchPage(0) : (page.value = 0)
    } else {
      await api.playlists().update(editingId.value!, payload)
      fetchPage(page.value)
    }
    dialogOpen.value = false
  } catch {
    formError.value = dialogMode.value === 'create' ? t('playlists.createFailed') : t('playlists.saveFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-4">

    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">{{ t('playlists.title') }}</h1>
      <div class="flex items-center gap-2">
        <NameFilter v-model="nameFilter" />
        <Button @click="openCreate">
          <Plus class="h-4 w-4" />
          {{ t('playlists.new') }}
        </Button>
      </div>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <div class="rounded-md border border-border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{{ t('common.name') }}</TableHead>
            <TableHead>{{ t('common.description') }}</TableHead>
            <TableHead class="w-36">{{ t('common.status') }}</TableHead>
            <TableHead class="w-44">{{ t('common.lead') }}</TableHead>
            <TableHead class="w-36">{{ t('common.created') }}</TableHead>
            <TableHead class="w-36">{{ t('common.updated') }}</TableHead>
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
                {{ t('playlists.empty') }}
              </TableCell>
            </TableRow>
          </template>
        </TableBody>
      </Table>
    </div>

    <!-- Pagination -->
    <div v-if="data && data.totalPages! > 1" class="flex items-center justify-between text-sm text-muted-foreground">
      <span>
        {{ t('playlists.count', data.totalElements!) }} —
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

  <!-- Create / Edit dialog -->
  <Dialog v-model:open="dialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ dialogTitle }}</DialogTitle>
      </DialogHeader>

      <form class="space-y-4" @submit.prevent="submitForm">
        <div class="space-y-1.5">
          <Label for="name">{{ t('common.name') }} <span class="text-destructive">*</span></Label>
          <Input id="name" v-model="form.name" :placeholder="t('playlists.namePlaceholder')" autofocus />
        </div>
        <div class="space-y-1.5">
          <Label for="description">{{ t('common.description') }}</Label>
          <Input id="description" v-model="form.description" :placeholder="t('playlists.descriptionPlaceholder')" />
        </div>
        <div v-if="dialogMode === 'create'" class="space-y-1.5">
          <Label>{{ t('playlists.ratingType') }}</Label>
          <Select v-model="form.ratingType" :placeholder="t('common.none')">
            <SelectItem v-for="rt in RATING_TYPES" :key="rt" :value="rt">
              {{ t(`ratingType.${rt}`) }}
            </SelectItem>
          </Select>
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
