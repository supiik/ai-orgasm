<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '@/api'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import PlaylistStatusBadge from '@/components/PlaylistStatusBadge.vue'

interface Contributor {
  id: string
  name: string
  avatarUrl?: string | null
}

interface Song {
  id: string
  artist: string
  name: string
  album?: string | null
}

interface Nomination {
  id: string
  songId: string
}

interface Playlist {
  id: string
  name: string
  description?: string | null
  status: string
  leadContributorName?: string | null
  leadContributorAvatarUrl?: string | null
}

const playlists = ref<Playlist[]>([])
const selected = ref<Playlist | null>(null)
const nominations = ref<Nomination[]>([])
const songs = ref<Record<string, Song>>({})
const contributors = ref<Contributor[]>([])
const guesses = ref<Record<string, string>>({})
const submitted = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    const { data } = await api.playlists().list(0, 100)
    playlists.value = ((data as any).content ?? []).filter((p: Playlist) => p.status === 'GUESSING')
  } catch {
    error.value = 'Failed to load playlists.'
  }

  try {
    const { data } = await api.contributors().list(0, 100)
    contributors.value = (data as any).content ?? []
  } catch {
    // ignore
  }
})

async function selectPlaylist(playlist: Playlist) {
  selected.value = playlist
  submitted.value = false
  guesses.value = {}
  nominations.value = []
  songs.value = {}
  loading.value = true
  error.value = null
  try {
    const { data } = await api.nominations().list(playlist.id, 0, 100)
    const approved = ((data as any).content ?? []).filter((n: any) => n.status === 'APPROVED')
    nominations.value = approved
    await Promise.all(approved.map(async (n: Nomination) => {
      if (!songs.value[n.songId]) {
        const { data: song } = await api.songs().get(n.songId)
        songs.value[n.songId] = song as Song
      }
    }))
  } catch {
    error.value = 'Failed to load nominations.'
  } finally {
    loading.value = false
  }
}

function submit() {
  submitted.value = true
}

function contributorName(id: string) {
  return contributors.value.find(c => c.id === id)?.name ?? id
}

const allGuessed = () => nominations.value.every(n => guesses.value[n.id])
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Guessing</h1>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <div v-if="!selected">
      <p class="text-sm text-muted-foreground mb-4">Select a playlist in guessing phase to start.</p>

      <div v-if="playlists.length === 0" class="text-sm text-muted-foreground py-10 text-center">
        No playlists are currently in the guessing phase.
      </div>

      <div class="rounded-md border border-border">
        <Table v-if="playlists.length > 0">
          <TableHeader>
            <TableRow>
              <TableHead>Playlist</TableHead>
              <TableHead>Description</TableHead>
              <TableHead class="w-36">Status</TableHead>
              <TableHead class="w-44">Lead</TableHead>
              <TableHead class="w-24" />
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="playlist in playlists"
              :key="playlist.id"
              class="cursor-pointer"
              @click="selectPlaylist(playlist)"
            >
              <TableCell class="font-medium">{{ playlist.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ playlist.description ?? '—' }}</TableCell>
              <TableCell><PlaylistStatusBadge :status="playlist.status" /></TableCell>
              <TableCell>
                <div v-if="playlist.leadContributorName" class="flex items-center gap-2">
                  <img v-if="playlist.leadContributorAvatarUrl" :src="playlist.leadContributorAvatarUrl" :alt="playlist.leadContributorName" class="w-6 h-6 rounded-full object-cover shrink-0" />
                  <div v-else class="w-6 h-6 rounded-full bg-muted shrink-0" />
                  <span class="text-sm">{{ playlist.leadContributorName }}</span>
                </div>
                <span v-else class="text-muted-foreground">—</span>
              </TableCell>
              <TableCell>
                <Button size="sm" variant="outline" @click.stop="selectPlaylist(playlist)">Play</Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </div>

    <div v-else class="space-y-4">
      <div class="flex items-center gap-3">
        <Button variant="ghost" size="sm" @click="selected = null">← Back</Button>
        <h2 class="text-lg font-medium">{{ selected.name }}</h2>
        <PlaylistStatusBadge :status="selected.status" />
      </div>

      <p class="text-sm text-muted-foreground">Guess who nominated each song. Nominators are hidden — can you figure it out?</p>

      <div v-if="loading" class="text-sm text-muted-foreground">Loading nominations…</div>

      <div v-else-if="nominations.length === 0" class="text-sm text-muted-foreground py-6 text-center">
        No approved nominations found for this playlist.
      </div>

      <div v-else class="rounded-md border border-border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>#</TableHead>
              <TableHead>Song</TableHead>
              <TableHead>Album</TableHead>
              <TableHead class="w-56">Your guess</TableHead>
              <TableHead v-if="submitted" class="w-40">Nominator</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="(nomination, idx) in nominations" :key="nomination.id">
              <TableCell class="text-muted-foreground">{{ idx + 1 }}</TableCell>
              <TableCell>
                <div v-if="songs[nomination.songId]">
                  <span class="font-medium">{{ songs[nomination.songId].name }}</span>
                  <span class="text-muted-foreground"> — {{ songs[nomination.songId].artist }}</span>
                </div>
                <span v-else class="text-muted-foreground text-sm">Loading…</span>
              </TableCell>
              <TableCell class="text-muted-foreground">{{ songs[nomination.songId]?.album ?? '—' }}</TableCell>
              <TableCell>
                <Select v-model="guesses[nomination.id]" :disabled="submitted">
                  <SelectTrigger class="w-full">
                    <SelectValue placeholder="Pick a contributor…" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="c in contributors" :key="c.id" :value="c.id">
                      {{ c.name }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </TableCell>
              <TableCell v-if="submitted">
                <span :class="guesses[nomination.id] === (nomination as any).nominatedById ? 'text-green-600 font-medium' : 'text-destructive'">
                  {{ contributorName((nomination as any).nominatedById) }}
                  {{ guesses[nomination.id] === (nomination as any).nominatedById ? '✓' : '✗' }}
                </span>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>

      <div v-if="nominations.length > 0 && !submitted" class="flex justify-end">
        <Button :disabled="!allGuessed()" @click="submit">Submit guesses</Button>
      </div>

      <div v-if="submitted" class="rounded-md border border-border p-4 text-sm">
        <p class="font-medium mb-1">Results</p>
        <p class="text-muted-foreground">
          You got
          <span class="text-foreground font-semibold">
            {{ nominations.filter(n => guesses[n.id] === (n as any).nominatedById).length }}
          </span>
          out of
          <span class="text-foreground font-semibold">{{ nominations.length }}</span>
          correct.
        </p>
      </div>
    </div>
  </div>
</template>
