<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { type ContributorPage, type ContributorResponse } from '@orgasm/backend-client'
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
const data = ref<ContributorPage | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function fetchPage(p: number) {
  loading.value = true
  error.value = null
  try {
    const { data: body } = await api.contributors().list(p, PAGE_SIZE, 'id', nameFilter.value || undefined)
    data.value = body
  } catch {
    error.value = 'Failed to load contributors.'
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
const form = ref({ name: '', email: '', avatarUrl: '' })
const formError = ref<string | null>(null)
const saving = ref(false)

const dialogTitle = computed(() => dialogMode.value === 'create' ? 'New contributor' : 'Edit contributor')
const submitLabel = computed(() => {
  if (saving.value) return dialogMode.value === 'create' ? 'Creating…' : 'Saving…'
  return dialogMode.value === 'create' ? 'Create' : 'Save'
})

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.value = { name: '', email: '', avatarUrl: '' }
  formError.value = null
  dialogOpen.value = true
}

function openEdit(contributor: ContributorResponse) {
  dialogMode.value = 'edit'
  editingId.value = contributor.id!
  form.value = { name: contributor.name!, email: contributor.email ?? '', avatarUrl: contributor.avatarUrl ?? '' }
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
      email: form.value.email.trim() || undefined,
      avatarUrl: form.value.avatarUrl.trim() || undefined,
    }
    if (dialogMode.value === 'create') {
      await api.contributors().create(payload)
      page.value === 0 ? fetchPage(0) : (page.value = 0)
    } else {
      await api.contributors().update(editingId.value!, payload)
      fetchPage(page.value)
    }
    dialogOpen.value = false
  } catch {
    formError.value = `Failed to ${dialogMode.value === 'create' ? 'create' : 'save'} contributor. Please try again.`
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-4">

    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">Contributors</h1>
      <div class="flex items-center gap-2">
        <NameFilter v-model="nameFilter" placeholder="Filter by name…" />
        <Button @click="openCreate">
          <Plus class="h-4 w-4" />
          New contributor
        </Button>
      </div>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <div class="rounded-md border border-border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-16">ID</TableHead>
            <TableHead class="w-10" />
            <TableHead>Name</TableHead>
            <TableHead>Email</TableHead>
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
              v-for="contributor in data.content"
              :key="contributor.id"
              class="cursor-pointer"
              @click="router.push({ name: 'contributor-detail', params: { id: contributor.id } })"
            >
              <TableCell class="text-muted-foreground">{{ contributor.id }}</TableCell>
              <TableCell>
                <img v-if="contributor.avatarUrl" :src="contributor.avatarUrl" :alt="contributor.name ?? ''" class="h-7 w-7 rounded-full object-cover" />
                <div v-else class="h-7 w-7 rounded-full bg-muted flex items-center justify-center text-xs text-muted-foreground font-medium">
                  {{ (contributor.name ?? '?')[0].toUpperCase() }}
                </div>
              </TableCell>
              <TableCell class="font-medium">{{ contributor.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ contributor.email ?? '—' }}</TableCell>
              <TableCell class="text-muted-foreground">{{ formatDate(contributor.createdAt!) }}</TableCell>
              <TableCell class="text-muted-foreground">{{ formatDate(contributor.updatedAt!) }}</TableCell>
              <TableCell>
                <Button variant="ghost" size="icon" @click.stop="openEdit(contributor)">
                  <Pencil class="h-4 w-4" />
                </Button>
              </TableCell>
            </TableRow>
          </template>
          <template v-else>
            <TableRow>
              <TableCell colspan="7" class="text-center text-muted-foreground py-10">
                No contributors found.
              </TableCell>
            </TableRow>
          </template>
        </TableBody>
      </Table>
    </div>

    <!-- Pagination -->
    <div v-if="data && data.totalPages! > 1" class="flex items-center justify-between text-sm text-muted-foreground">
      <span>
        {{ data.totalElements }} contributor{{ data.totalElements !== 1 ? 's' : '' }} —
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
          <Input id="name" v-model="form.name" placeholder="Full name" autofocus />
        </div>
        <div class="space-y-1.5">
          <Label for="email">Email</Label>
          <Input id="email" v-model="form.email" placeholder="contact@example.com" type="email" />
        </div>
        <div class="space-y-1.5">
          <Label for="avatarUrl">Avatar URL</Label>
          <Input id="avatarUrl" v-model="form.avatarUrl" placeholder="https://example.com/avatar.jpg" type="url" />
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
