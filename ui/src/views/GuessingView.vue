<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Select, SelectItem } from '@/components/ui/select'
import ContributorSelect from '@/components/ContributorSelect.vue'
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
  url?: string | null
}

interface Nomination {
  id: string
  songId: string
  nominatedById: string
  status: string
}

interface Playlist {
  id: string
  name: string
  description?: string | null
  status: string
  guessingDeadline?: string | null
  leadContributorName?: string | null
  leadContributorAvatarUrl?: string | null
}

// ── State ──────────────────────────────────────────────────────────────────

const router = useRouter()
const authStore = useAuthStore()
const isMock = import.meta.env.VITE_MOCK === 'true'

const playlists = ref<Playlist[]>([])
const allContributors = ref<Contributor[]>([])
const error = ref<string | null>(null)

const selectedPlaylist = ref<Playlist | null>(null)
const nominations = ref<Nomination[]>([])
const songs = ref<Record<string, Song>>({})
const loadingNominations = ref(false)

const me = ref<string>('')
const guesses = ref<Record<string, string>>({})

// ── Derived ────────────────────────────────────────────────────────────────

const approvedNominations = computed(() =>
  nominations.value.filter(n => n.status === 'APPROVED')
)

// contributors who have an approved nomination in this playlist
const eligibleContributors = computed(() => {
  const ids = new Set(approvedNominations.value.map(n => n.nominatedById))
  return allContributors.value.filter(c => ids.has(c.id))
})

// my approved nomination (hidden from guessing)
const myNomination = computed(() =>
  approvedNominations.value.find(n => n.nominatedById === me.value) ?? null
)

// songs I need to guess: all approved nominations except my own
const guessList = computed(() =>
  approvedNominations.value.filter(n => n.nominatedById !== me.value)
)

// contributors I can pick from: other approved contributors (not me)
const guessOptions = computed(() =>
  eligibleContributors.value.filter(c => c.id !== me.value)
)

const allGuessed = computed(() =>
  guessList.value.length > 0 && guessList.value.every(n => guesses.value[n.id])
)

// ── Data loading ───────────────────────────────────────────────────────────

onMounted(async () => {
  try {
    const [playlistsRes, contributorsRes] = await Promise.all([
      api.playlists().list(0, 100),
      api.contributors().list(0, 100),
    ])
    playlists.value = ((playlistsRes.data as any).content ?? []).filter(
      (p: Playlist) => p.status === 'GUESSING'
    )
    allContributors.value = (contributorsRes.data as any).content ?? []
  } catch {
    error.value = 'Failed to load data.'
  }
})

async function selectPlaylist(playlist: Playlist) {
  selectedPlaylist.value = playlist
  guesses.value = {}
  me.value = ''
  nominations.value = []
  songs.value = {}
  loadingNominations.value = true
  error.value = null
  try {
    const { data } = await api.nominations().list(playlist.id, 0, 100)
    nominations.value = (data as any).content ?? []
    await Promise.all(
      nominations.value
        .filter(n => n.status === 'APPROVED')
        .map(async n => {
          if (!songs.value[n.songId]) {
            const { data: song } = await api.songs().get(n.songId)
            songs.value[n.songId] = song as Song
          }
        })
    )
    const myId = authStore.currentContributor?.id
    if (myId && eligibleContributors.value.some(c => c.id === myId)) {
      me.value = myId
    }
  } catch {
    error.value = 'Failed to load nominations.'
  } finally {
    loadingNominations.value = false
  }
}

function formatDate(iso: string | null | undefined) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString(undefined, { dateStyle: 'medium' })
}

const guessingDeadlinePassed = computed(() => {
  if (!selectedPlaylist.value?.guessingDeadline) return false
  return new Date(selectedPlaylist.value.guessingDeadline) < new Date()
})

function contributorName(id: string) {
  return allContributors.value.find(c => c.id === id)?.name ?? id
}

function availableOptions(nominationId: string) {
  const usedElsewhere = new Set(
    Object.entries(guesses.value)
      .filter(([nid]) => nid !== nominationId)
      .map(([, cid]) => cid)
  )
  return guessOptions.value
    .filter(c => !usedElsewhere.has(c.id))
    .map(c => ({ id: c.id, name: c.name, avatarUrl: c.avatarUrl ?? null }))
}

async function submit() {
  if (!selectedPlaylist.value || !me.value) return
  const playlistId = selectedPlaylist.value.id
  const guessItems = Object.entries(guesses.value).map(([nominationId, guessedContributorId]) => ({
    nominationId,
    guessedContributorId,
  }))
  try {
    await api.playlists().submitGuesses(playlistId, {
      contributorId: me.value,
      guesses: guessItems,
    })
  } catch {
    // submission recording failed — navigate anyway
  }
  router.push(`/playlists/${playlistId}`)
}

function downloadPlaylist() {
  if (!selectedPlaylist.value) return
  const rows = approvedNominations.value.map((n, i) => {
    const s = songs.value[n.songId]
    const cell = (v: string) => `"${(v ?? '').replace(/"/g, '""')}"`
    return [i + 1, cell(s?.name ?? ''), cell(s?.artist ?? ''), cell(s?.album ?? ''), cell(s?.url ?? '')].join(',')
  })
  const csv = ['#,Song,Artist,Album,Link', ...rows].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${selectedPlaylist.value.name.replace(/[^a-z0-9]/gi, '_')}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}


</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Guessing</h1>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <!-- Playlist list -->
    <div v-if="!selectedPlaylist">
      <p class="text-sm text-muted-foreground mb-4">Select a playlist in the guessing phase to play.</p>

      <div v-if="playlists.length === 0" class="text-sm text-muted-foreground py-10 text-center">
        No playlists are currently in the guessing phase.
      </div>

      <div v-else class="rounded-md border border-border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Playlist</TableHead>
              <TableHead>Description</TableHead>
              <TableHead class="w-36">Status</TableHead>
              <TableHead class="w-44">Lead</TableHead>
              <TableHead class="w-36">Guessing deadline</TableHead>
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
              <TableCell class="text-muted-foreground text-sm">{{ formatDate(playlist.guessingDeadline) }}</TableCell>
              <TableCell>
                <Button size="sm" variant="outline" @click.stop="selectPlaylist(playlist)">Play</Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </div>

    <!-- Guessing game -->
    <div v-else class="space-y-5">
      <div class="flex items-center gap-3">
        <Button variant="ghost" size="sm" @click="selectedPlaylist = null">← Back</Button>
        <h2 class="text-lg font-medium">{{ selectedPlaylist.name }}</h2>
        <PlaylistStatusBadge :status="selectedPlaylist.status" />
        <Button variant="outline" size="sm" class="ml-auto" @click="downloadPlaylist">Download playlist</Button>
      </div>

      <p v-if="selectedPlaylist.guessingDeadline" class="text-sm text-muted-foreground">
        Guessing closes
        <span :class="guessingDeadlinePassed ? 'text-destructive font-medium' : 'text-foreground'">
          {{ formatDate(selectedPlaylist.guessingDeadline) }}
        </span>
        <span v-if="guessingDeadlinePassed"> (passed)</span>
      </p>

      <div v-if="loadingNominations" class="text-sm text-muted-foreground">Loading…</div>

      <template v-else-if="approvedNominations.length === 0">
        <p class="text-sm text-muted-foreground">No approved nominations in this playlist.</p>
      </template>

      <template v-else>
        <!-- Identity section -->
        <div class="flex items-center gap-3">
          <label class="text-sm font-medium whitespace-nowrap">I am</label>
          <!-- Auto-resolved in real mode -->
          <template v-if="me && !isMock">
            <span class="text-sm font-medium">{{ contributorName(me) }}</span>
            <button class="text-xs text-muted-foreground underline" @click="me = ''">Change</button>
          </template>
          <!-- Manual picker in mock mode or when identity is not resolved -->
          <Select v-else v-model="me" class="w-56" placeholder="Select your name…">
            <SelectItem v-for="c in eligibleContributors" :key="c.id" :value="c.id">
              {{ c.name }}
            </SelectItem>
          </Select>
          <span v-if="me" class="text-sm text-muted-foreground">
            You nominated <strong>{{ songs[myNomination?.songId ?? '']?.name ?? '…' }}</strong> —
            now guess the other {{ guessList.length }} song{{ guessList.length !== 1 ? 's' : '' }}.
          </span>
        </div>

        <!-- Guess table -->
        <div v-if="me" class="rounded-md border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>#</TableHead>
                <TableHead>Song</TableHead>
                <TableHead>Album</TableHead>
                <TableHead class="w-56">Your guess</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="(nomination, idx) in guessList" :key="nomination.id">
                <TableCell class="text-muted-foreground">{{ idx + 1 }}</TableCell>
                <TableCell>
                  <div v-if="songs[nomination.songId]">
                    <span class="font-medium">{{ songs[nomination.songId].name }}</span>
                    <span class="text-muted-foreground"> — {{ songs[nomination.songId].artist }}</span>
                  </div>
                </TableCell>
                <TableCell class="text-muted-foreground">{{ songs[nomination.songId]?.album ?? '—' }}</TableCell>
                <TableCell>
                  <ContributorSelect
                    v-model="guesses[nomination.id]"
                    :contributors="availableOptions(nomination.id)"
                    placeholder="Pick a contributor…"
                    class="w-full"
                  />
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>

        <!-- Actions -->
        <div v-if="me" class="flex justify-end">
          <Button :disabled="!allGuessed" @click="submit">Submit guesses</Button>
        </div>
      </template>
    </div>
  </div>
</template>
