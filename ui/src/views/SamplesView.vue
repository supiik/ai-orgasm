<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { type SampleResponse, type SampleStatus } from '@pi2/anchor-client'
import { api } from '@/api'
import { ChevronLeft, ChevronRight, Plus, Pencil } from 'lucide-vue-next'
import NameFilter from '@/components/NameFilter.vue'
import SampleStatusBadge from '@/components/SampleStatusBadge.vue'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectItem } from '@/components/ui/select'

const router = useRouter()

// ── Table ────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 10
const page = ref(0)
const nameFilter = ref('')

interface SamplePage {
  content?: SampleResponse[]
  totalElements?: number
  totalPages?: number
  number?: number
}

const data = ref<SamplePage | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function fetchPage(p: number) {
  loading.value = true
  error.value = null
  try {
    const { data: body } = await api.samples().list(p, PAGE_SIZE, 'id', nameFilter.value || undefined)
    data.value = body as SamplePage
  } catch {
    error.value = 'Failed to load samples.'
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

const STATUSES: SampleStatus[] = ['DRAFT', 'ACTIVE', 'ARCHIVED']

const dialogOpen = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<string | null>(null)
const form = ref({
  name: '',
  description: '',
  email: '',
  quantity: 0,
  largeNumber: 0,
  rating: 0,
  price: '',
  active: true,
  birthDate: '',
  scheduledAt: '',
  status: 'DRAFT' as SampleStatus,
  notes: '',
})
const formError = ref<string | null>(null)
const saving = ref(false)

const dialogTitle = computed(() => dialogMode.value === 'create' ? 'New sample' : 'Edit sample')
const submitLabel = computed(() => {
  if (saving.value) return dialogMode.value === 'create' ? 'Creating…' : 'Saving…'
  return dialogMode.value === 'create' ? 'Create' : 'Save'
})

function resetForm() {
  form.value = {
    name: '', description: '', email: '',
    quantity: 0, largeNumber: 0, rating: 0,
    price: '', active: true,
    birthDate: '', scheduledAt: '',
    status: 'DRAFT', notes: '',
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  formError.value = null
  dialogOpen.value = true
}

function openEdit(s: SampleResponse) {
  dialogMode.value = 'edit'
  editingId.value = s.id!
  form.value = {
    name: s.name ?? '',
    description: s.description ?? '',
    email: s.email ?? '',
    quantity: s.quantity ?? 0,
    largeNumber: Number(s.largeNumber ?? 0),
    rating: s.rating ?? 0,
    price: s.price != null ? String(s.price) : '',
    active: s.active ?? true,
    birthDate: s.birthDate ?? '',
    scheduledAt: s.scheduledAt ? s.scheduledAt.slice(0, 16) : '',
    status: s.status ?? 'DRAFT',
    notes: s.notes ?? '',
  }
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
      email: form.value.email.trim() || undefined,
      quantity: Number(form.value.quantity),
      largeNumber: Number(form.value.largeNumber),
      rating: Number(form.value.rating),
      price: form.value.price ? Number(form.value.price) : undefined,
      active: form.value.active,
      birthDate: form.value.birthDate || undefined,
      scheduledAt: form.value.scheduledAt ? form.value.scheduledAt + ':00' : undefined,
      status: form.value.status,
      notes: form.value.notes.trim() || undefined,
    }
    if (dialogMode.value === 'create') {
      await api.samples().create(payload)
      page.value === 0 ? fetchPage(0) : (page.value = 0)
    } else {
      await api.samples().update(editingId.value!, payload)
      fetchPage(page.value)
    }
    dialogOpen.value = false
  } catch {
    formError.value = `Failed to ${dialogMode.value === 'create' ? 'create' : 'save'} sample. Please try again.`
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-4">

    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">Samples</h1>
      <div class="flex items-center gap-2">
        <NameFilter v-model="nameFilter" placeholder="Filter by name…" />
        <Button @click="openCreate">
          <Plus class="h-4 w-4" />
          New sample
        </Button>
      </div>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <div class="rounded-md border border-border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Email</TableHead>
            <TableHead class="w-20 text-right">Qty</TableHead>
            <TableHead class="w-20 text-right">Rating</TableHead>
            <TableHead class="w-20 text-right">Price</TableHead>
            <TableHead class="w-16">Active</TableHead>
            <TableHead class="w-28">Birth date</TableHead>
            <TableHead class="w-32">Status</TableHead>
            <TableHead class="w-32">Created</TableHead>
            <TableHead class="w-10" />
          </TableRow>
        </TableHeader>
        <TableBody>
          <template v-if="loading">
            <TableRow v-for="i in PAGE_SIZE" :key="i">
              <TableCell colspan="10">
                <div class="h-4 rounded bg-muted animate-pulse" />
              </TableCell>
            </TableRow>
          </template>
          <template v-else-if="data && data.content?.length">
            <TableRow
              v-for="s in data.content"
              :key="s.id"
              class="cursor-pointer"
              @click="router.push({ name: 'sample-detail', params: { id: s.id } })"
            >
              <TableCell class="font-medium">{{ s.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ s.email ?? '—' }}</TableCell>
              <TableCell class="text-right">{{ s.quantity }}</TableCell>
              <TableCell class="text-right">{{ s.rating?.toFixed(1) }}</TableCell>
              <TableCell class="text-right">{{ s.price != null ? Number(s.price).toFixed(2) : '—' }}</TableCell>
              <TableCell>
                <span class="text-xs font-medium" :class="s.active ? 'text-green-600' : 'text-muted-foreground'">
                  {{ s.active ? 'Yes' : 'No' }}
                </span>
              </TableCell>
              <TableCell class="text-muted-foreground">{{ s.birthDate ?? '—' }}</TableCell>
              <TableCell><SampleStatusBadge v-if="s.status" :status="s.status" /></TableCell>
              <TableCell class="text-muted-foreground">{{ s.createdAt ? formatDate(s.createdAt) : '—' }}</TableCell>
              <TableCell>
                <Button variant="ghost" size="icon" @click.stop="openEdit(s)">
                  <Pencil class="h-4 w-4" />
                </Button>
              </TableCell>
            </TableRow>
          </template>
          <template v-else>
            <TableRow>
              <TableCell colspan="10" class="text-center text-muted-foreground py-10">
                No samples found.
              </TableCell>
            </TableRow>
          </template>
        </TableBody>
      </Table>
    </div>

    <!-- Pagination -->
    <div v-if="data && (data.totalPages ?? 0) > 1" class="flex items-center justify-between text-sm text-muted-foreground">
      <span>
        {{ data.totalElements }} sample{{ data.totalElements !== 1 ? 's' : '' }} —
        page {{ (data.number ?? 0) + 1 }} of {{ data.totalPages }}
      </span>
      <div class="flex gap-1">
        <Button variant="outline" size="icon" :disabled="page === 0" @click="page--">
          <ChevronLeft class="h-4 w-4" />
        </Button>
        <Button variant="outline" size="icon" :disabled="page >= (data.totalPages ?? 1) - 1" @click="page++">
          <ChevronRight class="h-4 w-4" />
        </Button>
      </div>
    </div>

  </div>

  <!-- Create / Edit dialog -->
  <Dialog v-model:open="dialogOpen">
    <DialogContent class="max-w-lg max-h-[90vh] overflow-y-auto">
      <DialogHeader>
        <DialogTitle>{{ dialogTitle }}</DialogTitle>
      </DialogHeader>

      <form class="space-y-3" @submit.prevent="submitForm">
        <!-- String fields -->
        <div class="space-y-1.5">
          <Label for="name">Name <span class="text-destructive">*</span></Label>
          <Input id="name" v-model="form.name" placeholder="Widget" autofocus />
        </div>
        <div class="space-y-1.5">
          <Label for="description">Description</Label>
          <Input id="description" v-model="form.description" placeholder="Optional description" />
        </div>
        <div class="space-y-1.5">
          <Label for="email">Email</Label>
          <Input id="email" v-model="form.email" type="email" placeholder="user@example.com" />
        </div>

        <!-- Numeric fields -->
        <div class="grid grid-cols-3 gap-3">
          <div class="space-y-1.5">
            <Label for="quantity">Quantity (int)</Label>
            <Input id="quantity" v-model.number="form.quantity" type="number" min="0" max="10000" />
          </div>
          <div class="space-y-1.5">
            <Label for="largeNumber">Large # (long)</Label>
            <Input id="largeNumber" v-model.number="form.largeNumber" type="number" />
          </div>
          <div class="space-y-1.5">
            <Label for="rating">Rating (0–10)</Label>
            <Input id="rating" v-model.number="form.rating" type="number" min="0" max="10" step="0.1" />
          </div>
        </div>
        <div class="space-y-1.5">
          <Label for="price">Price (decimal)</Label>
          <Input id="price" v-model="form.price" type="number" step="0.01" placeholder="9.99" />
        </div>

        <!-- Boolean field -->
        <div class="flex items-center gap-2">
          <input id="active" v-model="form.active" type="checkbox" class="h-4 w-4 rounded border-border" />
          <Label for="active">Active (boolean)</Label>
        </div>

        <!-- Date fields -->
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1.5">
            <Label for="birthDate">Birth date (date)</Label>
            <Input id="birthDate" v-model="form.birthDate" type="date" />
          </div>
          <div class="space-y-1.5">
            <Label for="scheduledAt">Scheduled at (datetime)</Label>
            <Input id="scheduledAt" v-model="form.scheduledAt" type="datetime-local" />
          </div>
        </div>

        <!-- Enum field -->
        <div class="space-y-1.5">
          <Label for="status">Status (enum)</Label>
          <Select id="status" v-model="form.status">
            <SelectItem v-for="s in STATUSES" :key="s" :value="s">{{ s }}</SelectItem>
          </Select>
        </div>

        <!-- Text/notes -->
        <div class="space-y-1.5">
          <Label for="notes">Notes (text)</Label>
          <Input id="notes" v-model="form.notes" placeholder="Free-form notes…" />
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
