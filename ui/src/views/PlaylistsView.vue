<script setup lang="ts">
import { ref, watch } from 'vue'
import axios from 'axios'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'

interface Playlist {
  id: number
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

interface PlaylistPage {
  content: Playlist[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

const PAGE_SIZE = 10

const page = ref(0)
const data = ref<PlaylistPage | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function fetchPage(p: number) {
  loading.value = true
  error.value = null
  try {
    const { data: body } = await axios.get<PlaylistPage>('/api/playlists', {
      params: { page: p, size: PAGE_SIZE, sort: 'id' },
    })
    data.value = body
  } catch {
    error.value = 'Failed to load playlists.'
  } finally {
    loading.value = false
  }
}

watch(page, fetchPage, { immediate: true })

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { dateStyle: 'medium' })
}
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-2xl font-semibold">Playlists</h1>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <div class="rounded-md border border-border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-16">ID</TableHead>
            <TableHead>Name</TableHead>
            <TableHead>Description</TableHead>
            <TableHead class="w-36">Created</TableHead>
            <TableHead class="w-36">Updated</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <template v-if="loading">
            <TableRow v-for="i in PAGE_SIZE" :key="i">
              <TableCell colspan="5">
                <div class="h-4 rounded bg-muted animate-pulse" />
              </TableCell>
            </TableRow>
          </template>
          <template v-else-if="data && data.content.length">
            <TableRow v-for="playlist in data.content" :key="playlist.id">
              <TableCell class="text-muted-foreground">{{ playlist.id }}</TableCell>
              <TableCell class="font-medium">{{ playlist.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ playlist.description ?? '—' }}</TableCell>
              <TableCell class="text-muted-foreground">{{ formatDate(playlist.createdAt) }}</TableCell>
              <TableCell class="text-muted-foreground">{{ formatDate(playlist.updatedAt) }}</TableCell>
            </TableRow>
          </template>
          <template v-else>
            <TableRow>
              <TableCell colspan="5" class="text-center text-muted-foreground py-10">
                No playlists found.
              </TableCell>
            </TableRow>
          </template>
        </TableBody>
      </Table>
    </div>

    <!-- Pagination -->
    <div v-if="data && data.totalPages > 1" class="flex items-center justify-between text-sm text-muted-foreground">
      <span>
        {{ data.totalElements }} playlist{{ data.totalElements !== 1 ? 's' : '' }} —
        page {{ data.number + 1 }} of {{ data.totalPages }}
      </span>
      <div class="flex gap-1">
        <Button
          variant="outline"
          size="icon"
          :disabled="page === 0"
          @click="page--"
        >
          <ChevronLeft class="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="icon"
          :disabled="page >= (data.totalPages - 1)"
          @click="page++"
        >
          <ChevronRight class="h-4 w-4" />
        </Button>
      </div>
    </div>
  </div>
</template>
