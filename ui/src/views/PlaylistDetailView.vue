<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type PlaylistResponse, type NominationResponse, PlaylistStatus, NominationStatus } from '@orgasm/backend-client'
import { api, type GuessEntry, type SongRatingEntry } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { ArrowLeft, Pencil, Play, Send, CheckCircle, XCircle, BookOpen, Headphones, Star } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PlaylistStatusBadge from '@/components/PlaylistStatusBadge.vue'
import ContributorSelect from '@/components/ContributorSelect.vue'
import SongUrlBadge from '@/components/SongUrlBadge.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isMock = import.meta.env.VITE_MOCK === 'true'

const isLead = computed(() =>
  isMock || authStore.isLeadOf((playlist.value as any)?.leadContributorId)
)

const id = route.params.id as string
const playlist = ref<PlaylistResponse | null>(null)
const nominations = ref<NominationResponse[]>([])
const songMap = ref<Record<string, { name: string; artist: string; url: string | null }>>({})
const contributorMap = ref<Record<string, { name: string; avatarUrl: string | null }>>({})
const guesses = ref<GuessEntry[]>([])
const songRatings = ref<SongRatingEntry[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const { data } = await api.playlists().get(id)
    playlist.value = data
    if (data.status === PlaylistStatus.Open || data.status === PlaylistStatus.Guessing || data.status === PlaylistStatus.Published) {
      await loadNominations()
    }
    if (data.status === PlaylistStatus.Guessing || data.status === PlaylistStatus.Published) {
      await loadGuesses()
    }
    if (data.status === PlaylistStatus.Published && (data as any).ratingType) {
      await loadSongRatings()
    }
  } catch {
    error.value = 'Playlist not found.'
  } finally {
    loading.value = false
  }
}

async function loadGuesses() {
  try {
    const { data } = await api.guesses().list(id)
    guesses.value = data
  } catch {}
}

async function loadSongRatings() {
  try {
    const { data } = await api.songRatings().list(id)
    songRatings.value = data
  } catch {}
}

async function loadNominations() {
  try {
    const { data } = await api.nominations().list(id)
    nominations.value = data.content ?? []
    await Promise.all([resolveNominationDetails(), loadAllContributors()])
  } catch {
    // non-critical
  }
}

async function loadAllContributors() {
  try {
    const { data } = await api.contributors().list(0, 100)
    allContributors.value = (data.content ?? []).map(c => ({ id: c.id!, name: c.name!, avatarUrl: c.avatarUrl ?? null }))
  } catch {}
}

async function resolveNominationDetails() {
  const songIds = [...new Set(nominations.value.map(n => n.songId!).filter(Boolean))]
  const contributorIds = [...new Set(nominations.value.map(n => n.nominatedById!).filter(Boolean))]
  await Promise.allSettled([
    ...songIds.map(async sid => {
      try {
        const { data } = await api.songs().get(sid)
        songMap.value[sid] = { name: data.name!, artist: data.artist!, url: data.url ?? null }
      } catch {}
    }),
    ...contributorIds.map(async cid => {
      try {
        const { data } = await api.contributors().get(cid)
        contributorMap.value[cid] = { name: data.name!, avatarUrl: data.avatarUrl ?? null }
      } catch {}
    }),
  ])
}

onMounted(load)

function formatDate(iso: string | null | undefined) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

const deadlinePassed = computed(() => {
  if (!playlist.value?.deadline) return false
  return new Date(playlist.value.deadline) < new Date()
})

const guessingDeadlinePassed = computed(() => {
  if (!(playlist.value as any)?.guessingDeadline) return false
  return new Date((playlist.value as any).guessingDeadline) < new Date()
})

// ── Edit dialog ───────────────────────────────────────────────────────────────

const editOpen = ref(false)
const editForm = ref({ name: '', description: '' })
const editError = ref<string | null>(null)
const saving = ref(false)

function openEdit() {
  editForm.value = { name: playlist.value!.name!, description: playlist.value!.description ?? '' }
  editError.value = null
  editOpen.value = true
}

async function submitEdit() {
  if (!editForm.value.name.trim()) { editError.value = 'Name is required.'; return }
  saving.value = true
  editError.value = null
  try {
    const { data } = await api.playlists().update(id, {
      name: editForm.value.name.trim(),
      description: editForm.value.description.trim() || undefined,
    })
    playlist.value = data
    editOpen.value = false
  } catch {
    editError.value = 'Failed to save. Please try again.'
  } finally {
    saving.value = false
  }
}

// ── Open playlist dialog ──────────────────────────────────────────────────────

const openOpen = ref(false)
const openForm = ref({ contributorId: '', deadline: '' })
const openError = ref<string | null>(null)
const opening = ref(false)

function showOpen() {
  const d = new Date(); d.setDate(d.getDate() + 14)
  const defaultDeadline = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  openForm.value = { contributorId: '', deadline: defaultDeadline }
  openError.value = null
  openOpen.value = true
  if (!allContributors.value.length) loadAllContributors()
}

async function submitOpen() {
  if (!openForm.value.contributorId.trim()) { openError.value = 'Contributor ID is required.'; return }
  if (!openForm.value.deadline) { openError.value = 'Deadline is required.'; return }
  opening.value = true
  openError.value = null
  try {
    const { data } = await api.playlists().open(id, {
      contributorId: openForm.value.contributorId.trim(),
      deadline: new Date(`${openForm.value.deadline}T00:00:00`).toISOString(),
    })
    playlist.value = data
    openOpen.value = false
    await loadNominations()
  } catch (e: unknown) {
    openError.value = extractDetail(e) ?? 'Failed to open playlist.'
  } finally {
    opening.value = false
  }
}

// ── Nominate song dialog ──────────────────────────────────────────────────────

const nominateOpen = ref(false)
const nominateForm = ref({ contributorId: '', artist: '', name: '', album: '', releaseYear: '', url: '' })
const nominateError = ref<string | null>(null)
const nominating = ref(false)
const allContributors = ref<Array<{ id: string; name: string; avatarUrl: string | null }>>([])

const visibleNominations = computed(() =>
  playlist.value?.status === PlaylistStatus.Published
    ? nominations.value.filter(n => n.status === NominationStatus.Approved)
    : nominations.value,
)

const allNominationsReviewed = computed(() =>
  nominations.value.length > 0 && nominations.value.every(n => n.status !== NominationStatus.Pending),
)

const eligibleContributors = computed(() => {
  const nominated = new Set(
    nominations.value
      .filter(n => n.status !== NominationStatus.Declined)
      .map(n => n.nominatedById)
      .filter(Boolean),
  )
  return allContributors.value.filter(c => !nominated.has(c.id))
})

const approvedNoms = computed(() =>
  nominations.value.filter(n => n.status === NominationStatus.Approved)
)

const guesserPool = computed(() => {
  const ids = new Set(approvedNoms.value.map(n => n.nominatedById).filter(Boolean))
  return allContributors.value
    .filter(c => ids.has(c.id))
    .sort((a, b) => a.name.localeCompare(b.name))
})

const guessMatrix = computed(() => {
  const m: Record<string, Record<string, string>> = {}
  for (const g of guesses.value) {
    if (!m[g.nominationId]) m[g.nominationId] = {}
    m[g.nominationId][g.guesserId] = g.guessedContributorId
  }
  return m
})

const allSubmitted = computed(() => {
  const submitted = new Set(guesses.value.map(g => g.guesserId))
  return guesserPool.value.length > 0 && guesserPool.value.every(c => submitted.has(c.id))
})

const songCorrectCounts = computed(() => {
  const result: Record<string, { correct: number; total: number }> = {}
  for (const nom of approvedNoms.value) {
    let correct = 0, total = 0
    for (const guesser of guesserPool.value) {
      if (guesser.id === nom.nominatedById) continue
      total++
      if (guessMatrix.value[nom.id!]?.[guesser.id] === nom.nominatedById) correct++
    }
    result[nom.id!] = { correct, total }
  }
  return result
})

const guesserCorrectCounts = computed(() => {
  const result: Record<string, { correct: number; total: number }> = {}
  for (const guesser of guesserPool.value) {
    let correct = 0, total = 0
    for (const nom of approvedNoms.value) {
      if (nom.nominatedById === guesser.id) continue
      total++
      if (guessMatrix.value[nom.id!]?.[guesser.id] === nom.nominatedById) correct++
    }
    result[guesser.id] = { correct, total }
  }
  return result
})

const showMatrix = computed(() =>
  playlist.value?.status === PlaylistStatus.Published ||
  (playlist.value?.status === PlaylistStatus.Guessing && allSubmitted.value)
)

// ── Song ratings ─────────────────────────────────────────────────────────────

const ratingType = computed(() => (playlist.value as any)?.ratingType as string | null)

const STAR_TO_POINTS: Record<string, number[]> = {
  LINEAR: [0, 1, 2, 3],
  FIBONACCI: [0, 5, 8, 13],
  BEST_SONG: [0, 1],
}

const maxStars = computed(() => ratingType.value === 'BEST_SONG' ? 1 : 3)

const ratingContributorId = ref('')
const myStars = ref<Record<string, number>>({})
const ratingError = ref<string | null>(null)
const submittingRating = ref(false)

function starsForNom(nomId: string): number {
  return myStars.value[nomId] ?? 0
}

function clickStar(nomId: string, star: number) {
  const current = starsForNom(nomId)
  if (current === star) {
    delete myStars.value[nomId]
  } else {
    if (ratingType.value === 'BEST_SONG') {
      myStars.value = { [nomId]: 1 }
    } else {
      const existing = Object.entries(myStars.value).find(([, v]) => v === star)
      if (existing) delete myStars.value[existing[0]]
      myStars.value[nomId] = star
    }
  }
  autoSubmitRatings()
}

const ratingComplete = computed(() => {
  const points = STAR_TO_POINTS[ratingType.value ?? '']
  if (!points) return false
  const expected = points.filter(p => p > 0).length
  return Object.keys(myStars.value).length === expected
})

async function autoSubmitRatings() {
  if (!ratingContributorId.value || !ratingComplete.value) return
  submittingRating.value = true
  ratingError.value = null
  try {
    const points = STAR_TO_POINTS[ratingType.value ?? ''] ?? []
    const ratings = Object.entries(myStars.value).map(([nominationId, stars]) => ({
      nominationId,
      points: points[stars] ?? stars,
    }))
    await api.songRatings().submit(id, { contributorId: ratingContributorId.value, ratings })
    await loadSongRatings()
  } catch (e: unknown) {
    ratingError.value = extractDetail(e) ?? 'Failed to submit ratings.'
  } finally {
    submittingRating.value = false
  }
}

const songTotalPoints = computed(() => {
  const result: Record<string, number> = {}
  for (const r of songRatings.value) {
    result[r.nominationId] = (result[r.nominationId] ?? 0) + r.points
  }
  return result
})

const ratingTypeLabel = computed(() => {
  if (ratingType.value === 'LINEAR') return 'Linear (1, 2, 3 pts)'
  if (ratingType.value === 'FIBONACCI') return 'Fibonacci (5, 8, 13 pts)'
  if (ratingType.value === 'BEST_SONG') return 'Best Song (pick one)'
  return ''
})

function showNominate(contributorId = '') {
  nominateForm.value = { contributorId, artist: '', name: '', album: '', releaseYear: '', url: '' }
  nominateError.value = null
  nominateOpen.value = true
}

async function submitNominate() {
  if (!nominateForm.value.contributorId || !nominateForm.value.artist.trim() || !nominateForm.value.name.trim()) {
    nominateError.value = 'Contributor, artist, and song name are required.'
    return
  }
  nominating.value = true
  nominateError.value = null
  try {
    const year = nominateForm.value.releaseYear ? parseInt(nominateForm.value.releaseYear) : undefined
    const { data: song } = await api.songs().create({
      artist: nominateForm.value.artist.trim(),
      name: nominateForm.value.name.trim(),
      album: nominateForm.value.album.trim() || undefined,
      releaseYear: year,
      url: nominateForm.value.url.trim() || undefined,
    })
    await api.nominations().create(id, {
      contributorId: nominateForm.value.contributorId,
      songId: song.id!,
    })
    nominateOpen.value = false
    await loadNominations()
  } catch (e: unknown) {
    nominateError.value = extractDetail(e) ?? 'Failed to nominate song.'
  } finally {
    nominating.value = false
  }
}

// ── Approve / Decline ─────────────────────────────────────────────────────────

async function reviewNomination(nomId: string, action: 'approve' | 'decline') {
  const reviewerId = playlist.value?.leadContributorId
  if (!reviewerId) return
  try {
    if (action === 'approve') {
      await api.nominations().approve(nomId, { reviewerId })
    } else {
      await api.nominations().decline(nomId, { reviewerId })
    }
    await loadNominations()
  } catch {}
}

// ── Start guessing ────────────────────────────────────────────────────────────

async function startGuessing() {
  const contributorId = playlist.value?.leadContributorId
  if (!contributorId) return
  try {
    const { data } = await api.playlists().startGuessing(id, { contributorId })
    playlist.value = data
    await loadNominations()
  } catch (e: unknown) {
    error.value = extractDetail(e) ?? 'Failed to start guessing phase.'
  }
}

// ── Publish dialog ────────────────────────────────────────────────────────────

const publishOpen = ref(false)
const publishContributorId = ref('')
const publishError = ref<string | null>(null)
const publishing = ref(false)

function showPublish() {
  publishContributorId.value = ''
  publishError.value = null
  publishOpen.value = true
}

async function submitPublish() {
  if (!publishContributorId.value.trim()) { publishError.value = 'Contributor ID is required.'; return }
  publishing.value = true
  publishError.value = null
  try {
    const { data } = await api.playlists().publish(id, { contributorId: publishContributorId.value.trim() })
    playlist.value = data
    publishOpen.value = false
    await loadGuesses()
  } catch (e: unknown) {
    publishError.value = extractDetail(e) ?? 'Failed to publish playlist.'
  } finally {
    publishing.value = false
  }
}

// ── helpers ───────────────────────────────────────────────────────────────────

function extractDetail(e: unknown): string | null {
  if (e && typeof e === 'object' && 'response' in e) {
    const r = (e as { response?: { data?: { detail?: string; message?: string; title?: string } } }).response
    return r?.data?.detail ?? r?.data?.message ?? r?.data?.title ?? null
  }
  if (e instanceof Error) return e.message
  return null
}

function statusLabel(s: NominationStatus | undefined) {
  if (s === NominationStatus.Approved) return 'Approved'
  if (s === NominationStatus.Declined) return 'Declined'
  return 'Pending'
}

function statusClass(s: NominationStatus | undefined) {
  if (s === NominationStatus.Approved) return 'text-green-600'
  if (s === NominationStatus.Declined) return 'text-destructive'
  return 'text-muted-foreground'
}
</script>

<template>
  <div class="space-y-6 max-w-3xl">

    <!-- Header -->
    <div class="flex items-center gap-3">
      <Button variant="ghost" size="icon" @click="router.back()">
        <ArrowLeft class="h-4 w-4" />
      </Button>
      <h1 class="text-2xl font-semibold">
        <span v-if="loading" class="inline-block w-48 h-7 rounded bg-muted animate-pulse" />
        <span v-else>{{ playlist?.name }}</span>
      </h1>
      <div class="ml-auto flex gap-2">
        <Button v-if="isLead && playlist?.status === PlaylistStatus.New" variant="outline" size="sm" @click="showOpen">
          <Play class="h-4 w-4" />
          Open for nominations
        </Button>
        <Button v-if="isLead && playlist?.status === PlaylistStatus.Open && (deadlinePassed || allNominationsReviewed)" variant="outline" size="sm" @click="startGuessing">
          <Headphones class="h-4 w-4" />
          Start guessing
        </Button>
        <Button v-if="isLead && playlist?.status === PlaylistStatus.Guessing" variant="outline" size="sm" @click="showPublish">
          <BookOpen class="h-4 w-4" />
          Publish
        </Button>
        <Button v-if="playlist?.status === PlaylistStatus.New || playlist?.status === PlaylistStatus.Open" variant="outline" size="sm" @click="openEdit">
          <Pencil class="h-4 w-4" />
          Edit
        </Button>
      </div>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <!-- Details -->
    <template v-if="playlist">
      <dl class="divide-y divide-border rounded-md border border-border text-sm overflow-hidden [&>div:nth-child(even)]:bg-muted/40">
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-36 shrink-0 text-muted-foreground">ID</dt>
          <dd>{{ playlist.id }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-36 shrink-0 text-muted-foreground">Name</dt>
          <dd class="font-medium">{{ playlist.name }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-36 shrink-0 text-muted-foreground">Description</dt>
          <dd class="text-muted-foreground">{{ playlist.description ?? '—' }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-36 shrink-0 text-muted-foreground">Status</dt>
          <dd><PlaylistStatusBadge v-if="playlist.status" :status="playlist.status" /></dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-36 shrink-0 text-muted-foreground">Lead contributor</dt>
          <dd>
            <RouterLink v-if="playlist.leadContributorId" :to="`/contributors/${playlist.leadContributorId}`" class="flex items-center gap-2 w-fit hover:underline underline-offset-2">
              <img v-if="(playlist as any).leadContributorAvatarUrl" :src="(playlist as any).leadContributorAvatarUrl" :alt="(playlist as any).leadContributorName" class="w-6 h-6 rounded-full object-cover shrink-0" />
              <div v-else class="w-6 h-6 rounded-full bg-muted shrink-0" />
              <span>{{ (playlist as any).leadContributorName ?? playlist.leadContributorId }}</span>
            </RouterLink>
            <span v-else class="text-muted-foreground">—</span>
          </dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-36 shrink-0 text-muted-foreground">Deadline</dt>
          <dd :class="deadlinePassed ? 'text-destructive' : ''">
            {{ formatDate(playlist.deadline) }}
            <span v-if="deadlinePassed" class="ml-1 text-xs">(passed)</span>
          </dd>
        </div>
        <div v-if="(playlist as any).guessingDeadline" class="flex px-4 py-3 gap-4">
          <dt class="w-36 shrink-0 text-muted-foreground">Guessing deadline</dt>
          <dd :class="guessingDeadlinePassed ? 'text-destructive' : ''">
            {{ formatDate((playlist as any).guessingDeadline) }}
            <span v-if="guessingDeadlinePassed" class="ml-1 text-xs">(passed)</span>
          </dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-36 shrink-0 text-muted-foreground">Created</dt>
          <dd>{{ formatDate(playlist.createdAt) }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-36 shrink-0 text-muted-foreground">Updated</dt>
          <dd>{{ formatDate(playlist.updatedAt) }}</dd>
        </div>
      </dl>

      <!-- Nominations section -->
      <section v-if="playlist.status === PlaylistStatus.Open || playlist.status === PlaylistStatus.Guessing || playlist.status === PlaylistStatus.Published">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-lg font-semibold">Nominations</h2>
          <Button v-if="playlist.status === PlaylistStatus.Open && !deadlinePassed" size="sm" @click="showNominate">
            <Send class="h-4 w-4" />
            Nominate song
          </Button>
        </div>

        <div v-if="visibleNominations.length === 0" class="text-sm text-muted-foreground py-4 text-center border border-border rounded-md">
          No nominations yet.
        </div>

        <div v-else class="divide-y divide-border rounded-md border border-border overflow-hidden [&>div:nth-child(even)]:bg-muted/40">
          <div v-for="nom in visibleNominations" :key="nom.id" class="grid grid-cols-3 items-center px-4 py-3 gap-4 text-sm">
            <!-- Song -->
            <div class="min-w-0">
              <div class="font-medium truncate">{{ songMap[nom.songId!]?.name ?? nom.songId }}</div>
              <div class="text-muted-foreground text-xs truncate">{{ songMap[nom.songId!]?.artist }}</div>
              <SongUrlBadge v-if="songMap[nom.songId!]?.url" :url="songMap[nom.songId!].url!" class="mt-1" />
            </div>
            <!-- Contributor -->
            <div class="flex items-center gap-2 min-w-0 text-xs text-muted-foreground">
              <img v-if="contributorMap[nom.nominatedById!]?.avatarUrl" :src="contributorMap[nom.nominatedById!].avatarUrl!" class="w-5 h-5 rounded-full object-cover shrink-0" :alt="contributorMap[nom.nominatedById!].name" />
              <span class="truncate">{{ contributorMap[nom.nominatedById!]?.name ?? nom.nominatedById }}</span>
            </div>
            <!-- Status / actions -->
            <div class="flex items-center justify-end gap-1">
              <template v-if="isLead && playlist.status === PlaylistStatus.Open">
                <Button size="sm" variant="ghost" :class="nom.status === NominationStatus.Approved ? 'text-green-600' : 'text-muted-foreground hover:text-green-600'" @click="reviewNomination(nom.id!, 'approve')">
                  <CheckCircle class="h-4 w-4" />
                </Button>
                <Button size="sm" variant="ghost" :class="nom.status === NominationStatus.Declined ? 'text-destructive' : 'text-muted-foreground hover:text-destructive'" @click="reviewNomination(nom.id!, 'decline')">
                  <XCircle class="h-4 w-4" />
                </Button>
              </template>
              <span v-else :class="['text-xs font-medium', statusClass(nom.status)]">{{ statusLabel(nom.status) }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Contributors not yet nominated -->
      <section v-if="playlist.status === PlaylistStatus.Open && !deadlinePassed && eligibleContributors.length > 0">
        <h2 class="text-lg font-semibold mb-3">Not yet nominated</h2>
        <div class="divide-y divide-border rounded-md border border-border overflow-hidden [&>div:nth-child(even)]:bg-muted/40">
          <div v-for="c in eligibleContributors" :key="c.id" class="flex items-center px-4 py-3 gap-3 text-sm">
            <img v-if="c.avatarUrl" :src="c.avatarUrl" :alt="c.name" class="w-7 h-7 rounded-full object-cover shrink-0" />
            <div v-else class="w-7 h-7 rounded-full bg-muted shrink-0" />
            <span class="flex-1">{{ c.name }}</span>
            <Button size="sm" variant="ghost" @click="showNominate(c.id)">
              <Send class="h-4 w-4" />
              Nominate
            </Button>
          </div>
        </div>
      </section>

      <!-- Rating type info -->
      <div v-if="ratingType" class="flex px-4 py-3 gap-4 border border-border rounded-md text-sm bg-muted/40">
        <dt class="w-36 shrink-0 text-muted-foreground">Rating type</dt>
        <dd class="font-medium">{{ ratingTypeLabel }}</dd>
      </div>

      <!-- Guess matrix -->
      <section v-if="showMatrix">
        <h2 class="text-lg font-semibold mb-3">Guess matrix</h2>
        <div class="overflow-x-auto rounded-md border border-border">
          <table class="text-sm w-full">
            <thead>
              <tr class="bg-muted/60 divide-x divide-border">
                <th class="px-4 py-2 text-left font-medium text-muted-foreground whitespace-nowrap">Song</th>
                <th v-for="guesser in guesserPool" :key="guesser.id" class="px-3 py-2 font-medium whitespace-nowrap text-center">
                  <div class="flex flex-col items-center gap-1">
                    <img v-if="guesser.avatarUrl" :src="guesser.avatarUrl" :alt="guesser.name" class="w-6 h-6 rounded-full object-cover" />
                    <div v-else class="w-6 h-6 rounded-full bg-muted" />
                    <span class="text-xs">{{ guesser.name }}</span>
                  </div>
                </th>
                <th class="px-3 py-2 font-medium whitespace-nowrap text-center text-muted-foreground">Total</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-border">
              <tr v-for="nom in approvedNoms" :key="nom.id" class="divide-x divide-border even:bg-muted/40">
                <td class="px-4 py-2 whitespace-nowrap">
                  <div class="font-medium">{{ songMap[nom.songId!]?.name ?? nom.songId }}</div>
                  <div class="text-xs text-muted-foreground">{{ songMap[nom.songId!]?.artist }}</div>
                </td>
                <td v-for="guesser in guesserPool" :key="guesser.id" class="px-3 py-2 text-center whitespace-nowrap">
                  <span v-if="guesser.id === nom.nominatedById" class="text-muted-foreground">—</span>
                  <template v-else-if="guessMatrix[nom.id!]?.[guesser.id]">
                    <span :class="guessMatrix[nom.id!][guesser.id] === nom.nominatedById ? 'text-green-600 font-medium' : 'text-destructive'">
                      {{ contributorMap[guessMatrix[nom.id!][guesser.id]]?.name ?? guessMatrix[nom.id!][guesser.id] }}
                    </span>
                  </template>
                  <span v-else class="text-muted-foreground">—</span>
                </td>
                <td class="px-3 py-2 text-center whitespace-nowrap font-medium tabular-nums">
                  <span :class="songCorrectCounts[nom.id!]?.correct === songCorrectCounts[nom.id!]?.total ? 'text-green-600' : songCorrectCounts[nom.id!]?.correct === 0 ? 'text-muted-foreground' : ''">
                    {{ songCorrectCounts[nom.id!]?.correct ?? 0 }}/{{ songCorrectCounts[nom.id!]?.total ?? 0 }}
                  </span>
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="bg-muted/60 divide-x divide-border border-t border-border">
                <td class="px-4 py-2 font-medium text-muted-foreground whitespace-nowrap">Total</td>
                <td v-for="guesser in guesserPool" :key="guesser.id" class="px-3 py-2 text-center whitespace-nowrap font-medium tabular-nums">
                  <span :class="guesserCorrectCounts[guesser.id]?.correct === guesserCorrectCounts[guesser.id]?.total ? 'text-green-600' : guesserCorrectCounts[guesser.id]?.correct === 0 ? 'text-muted-foreground' : ''">
                    {{ guesserCorrectCounts[guesser.id]?.correct ?? 0 }}/{{ guesserCorrectCounts[guesser.id]?.total ?? 0 }}
                  </span>
                </td>
                <td class="px-3 py-2 text-center text-muted-foreground">—</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      <!-- Song ratings section -->
      <section v-if="playlist.status === PlaylistStatus.Published && ratingType">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-lg font-semibold">Song Ratings</h2>
          <div class="flex items-center gap-2 text-sm">
            <span class="text-muted-foreground">Rate as:</span>
            <ContributorSelect
              v-model="ratingContributorId"
              :contributors="allContributors"
              placeholder="Select contributor…"
              class="w-48"
            />
          </div>
        </div>
        <p class="text-xs text-muted-foreground mb-3">{{ ratingTypeLabel }}</p>
        <p v-if="ratingError" class="text-sm text-destructive mb-3">{{ ratingError }}</p>

        <div class="divide-y divide-border rounded-md border border-border overflow-hidden">
          <div v-for="nom in approvedNoms" :key="nom.id" class="flex items-center px-4 py-3 gap-4 text-sm even:bg-muted/40">
            <div class="flex-1 min-w-0">
              <div class="font-medium truncate">{{ songMap[nom.songId!]?.name ?? nom.songId }}</div>
              <div class="text-xs text-muted-foreground truncate">{{ songMap[nom.songId!]?.artist }}</div>
            </div>
            <div class="flex items-center gap-0.5 shrink-0">
              <button
                v-for="star in maxStars"
                :key="star"
                :disabled="!ratingContributorId"
                class="p-0.5 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                @click="clickStar(nom.id!, star)"
              >
                <Star
                  class="h-5 w-5"
                  :class="starsForNom(nom.id!) >= star
                    ? 'text-yellow-500 fill-yellow-500'
                    : 'text-muted-foreground/40 hover:text-yellow-400'"
                />
              </button>
            </div>
            <div class="w-16 text-right tabular-nums shrink-0">
              <span class="font-semibold">{{ songTotalPoints[nom.id!] ?? 0 }}</span>
              <span class="text-muted-foreground text-xs"> pts</span>
            </div>
            <div class="flex flex-wrap gap-1 w-40 shrink-0">
              <span
                v-for="r in songRatings.filter(sr => sr.nominationId === nom.id)"
                :key="r.contributorId"
                class="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs"
              >
                {{ r.contributorName }}
                <span class="font-medium">{{ r.points }}</span>
              </span>
            </div>
          </div>
        </div>
      </section>
    </template>

  </div>

  <!-- Edit dialog -->
  <Dialog v-model:open="editOpen">
    <DialogContent>
      <DialogHeader><DialogTitle>Edit playlist</DialogTitle></DialogHeader>
      <form class="space-y-4" @submit.prevent="submitEdit">
        <div class="space-y-1.5">
          <Label for="edit-name">Name <span class="text-destructive">*</span></Label>
          <Input id="edit-name" v-model="editForm.name" placeholder="My playlist" autofocus />
        </div>
        <div class="space-y-1.5">
          <Label for="edit-desc">Description</Label>
          <Input id="edit-desc" v-model="editForm.description" placeholder="Optional description" />
        </div>
        <p v-if="editError" class="text-sm text-destructive">{{ editError }}</p>
      </form>
      <DialogFooter>
        <Button variant="outline" :disabled="saving" @click="editOpen = false">Cancel</Button>
        <Button :disabled="saving" @click="submitEdit">{{ saving ? 'Saving…' : 'Save' }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Open playlist dialog -->
  <Dialog v-model:open="openOpen">
    <DialogContent>
      <DialogHeader><DialogTitle>Open playlist for nominations</DialogTitle></DialogHeader>
      <form class="space-y-4" @submit.prevent="submitOpen">
        <div class="space-y-1.5">
          <Label>Lead contributor <span class="text-destructive">*</span></Label>
          <ContributorSelect v-model="openForm.contributorId" :contributors="allContributors" placeholder="Select lead contributor…" />
        </div>
        <div class="space-y-1.5">
          <Label for="open-deadline">Nomination deadline <span class="text-destructive">*</span></Label>
          <Input id="open-deadline" v-model="openForm.deadline" type="date" />
        </div>
        <p v-if="openError" class="text-sm text-destructive">{{ openError }}</p>
      </form>
      <DialogFooter>
        <Button variant="outline" :disabled="opening" @click="openOpen = false">Cancel</Button>
        <Button :disabled="opening" @click="submitOpen">{{ opening ? 'Opening…' : 'Open' }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Nominate song dialog -->
  <Dialog v-model:open="nominateOpen">
    <DialogContent>
      <DialogHeader><DialogTitle>Nominate a song</DialogTitle></DialogHeader>
      <form class="space-y-4" @submit.prevent="submitNominate">
        <div class="space-y-1.5">
          <Label>Contributor <span class="text-destructive">*</span></Label>
          <ContributorSelect v-model="nominateForm.contributorId" :contributors="eligibleContributors" placeholder="Select contributor…" />
        </div>
        <div class="border-t border-border pt-4 space-y-3">
          <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Song</p>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1.5">
              <Label for="nom-artist">Artist <span class="text-destructive">*</span></Label>
              <Input id="nom-artist" v-model="nominateForm.artist" placeholder="Radiohead" />
            </div>
            <div class="space-y-1.5">
              <Label for="nom-name">Title <span class="text-destructive">*</span></Label>
              <Input id="nom-name" v-model="nominateForm.name" placeholder="Creep" />
            </div>
            <div class="space-y-1.5">
              <Label for="nom-album">Album</Label>
              <Input id="nom-album" v-model="nominateForm.album" placeholder="Pablo Honey" />
            </div>
            <div class="space-y-1.5">
              <Label for="nom-year">Year</Label>
              <Input id="nom-year" v-model="nominateForm.releaseYear" type="number" placeholder="1993" />
            </div>
          </div>
          <div class="space-y-1.5">
            <Label for="nom-url">Link</Label>
            <Input id="nom-url" v-model="nominateForm.url" type="url" placeholder="https://…" />
          </div>
        </div>
        <p v-if="nominateError" class="text-sm text-destructive">{{ nominateError }}</p>
      </form>
      <DialogFooter>
        <Button variant="outline" :disabled="nominating" @click="nominateOpen = false">Cancel</Button>
        <Button :disabled="nominating" @click="submitNominate">{{ nominating ? 'Nominating…' : 'Nominate' }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Publish dialog -->
  <Dialog v-model:open="publishOpen">
    <DialogContent>
      <DialogHeader><DialogTitle>Publish playlist</DialogTitle></DialogHeader>
      <form class="space-y-4" @submit.prevent="submitPublish">
        <p class="text-sm text-muted-foreground">Confirm you are the lead contributor. This action cannot be undone.</p>
        <div class="space-y-1.5">
          <Label>Your contributor <span class="text-destructive">*</span></Label>
          <ContributorSelect v-model="publishContributorId" :contributors="allContributors" placeholder="Select your contributor…" />
        </div>
        <p v-if="publishError" class="text-sm text-destructive">{{ publishError }}</p>
      </form>
      <DialogFooter>
        <Button variant="outline" :disabled="publishing" @click="publishOpen = false">Cancel</Button>
        <Button :disabled="publishing" @click="submitPublish">{{ publishing ? 'Publishing…' : 'Publish' }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
