<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type SampleResponse } from '@pi2/anchor-client'
import { api } from '@/api'
import { ArrowLeft, Trash2 } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import SampleStatusBadge from '@/components/SampleStatusBadge.vue'

const route = useRoute()
const router = useRouter()
const id = route.params.id as string

const sample = ref<SampleResponse | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const deleting = ref(false)

onMounted(async () => {
  try {
    const { data } = await api.samples().get(id)
    sample.value = data
  } catch {
    error.value = 'Sample not found.'
  } finally {
    loading.value = false
  }
})

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { dateStyle: 'long' })
}

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

async function deleteSample() {
  if (!confirm('Delete this sample?')) return
  deleting.value = true
  try {
    await api.samples().delete(id)
    router.push({ name: 'samples' })
  } catch {
    error.value = 'Failed to delete sample.'
    deleting.value = false
  }
}
</script>

<template>
  <div class="space-y-6 max-w-2xl">
    <div class="flex items-center gap-3">
      <Button variant="ghost" size="icon" @click="router.back()">
        <ArrowLeft class="h-4 w-4" />
      </Button>
      <h1 class="text-2xl font-semibold">Sample detail</h1>
    </div>

    <div v-if="loading" class="space-y-3">
      <div v-for="i in 8" :key="i" class="h-6 rounded bg-muted animate-pulse" />
    </div>

    <div v-else-if="error" class="text-destructive text-sm">{{ error }}</div>

    <template v-else-if="sample">
      <div class="rounded-md border border-border divide-y divide-border">

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">ID</span>
          <span class="col-span-2 font-mono text-xs">{{ sample.id }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Name (String)</span>
          <span class="col-span-2">{{ sample.name }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Description (String)</span>
          <span class="col-span-2 text-muted-foreground">{{ sample.description ?? '—' }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Email (String)</span>
          <span class="col-span-2">{{ sample.email ?? '—' }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Quantity (int)</span>
          <span class="col-span-2">{{ sample.quantity }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Large number (long)</span>
          <span class="col-span-2">{{ sample.largeNumber }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Rating (double)</span>
          <span class="col-span-2">{{ sample.rating?.toFixed(2) }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Price (BigDecimal)</span>
          <span class="col-span-2">{{ sample.price != null ? Number(sample.price).toFixed(4) : '—' }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Active (boolean)</span>
          <span class="col-span-2" :class="sample.active ? 'text-green-600' : 'text-muted-foreground'">
            {{ sample.active ? 'Yes' : 'No' }}
          </span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Birth date (LocalDate)</span>
          <span class="col-span-2">{{ sample.birthDate ?? '—' }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Scheduled at (LocalDateTime)</span>
          <span class="col-span-2">{{ sample.scheduledAt ? formatDateTime(sample.scheduledAt) : '—' }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Status (enum)</span>
          <span class="col-span-2">
            <SampleStatusBadge v-if="sample.status" :status="sample.status" />
          </span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Notes (TEXT)</span>
          <span class="col-span-2 text-muted-foreground">{{ sample.notes ?? '—' }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Version</span>
          <span class="col-span-2">{{ sample.version }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Created at (Instant)</span>
          <span class="col-span-2">{{ sample.createdAt ? formatDateTime(sample.createdAt) : '—' }}</span>
        </div>

        <div class="grid grid-cols-3 px-4 py-3 text-sm">
          <span class="text-muted-foreground font-medium">Updated at (Instant)</span>
          <span class="col-span-2">{{ sample.updatedAt ? formatDateTime(sample.updatedAt) : '—' }}</span>
        </div>

      </div>

      <div class="flex justify-end">
        <Button variant="destructive" :disabled="deleting" @click="deleteSample">
          <Trash2 class="h-4 w-4 mr-1" />
          {{ deleting ? 'Deleting…' : 'Delete' }}
        </Button>
      </div>
    </template>
  </div>
</template>
