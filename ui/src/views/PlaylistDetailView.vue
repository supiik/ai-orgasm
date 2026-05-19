<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type PlaylistResponse, type NominationResponse, PlaylistStatus, NominationStatus } from '@orgasm/backend-client'
import { api } from '@/api'
import { ArrowLeft, Pencil, Play, Send, CheckCircle, XCircle, BookOpen, Check } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { SelectItem, SelectItemText, SelectItemIndicator } from 'radix-vue'
import PlaylistStatusBadge from '@/components/PlaylistStatusBadge.vue'

const route = useRoute()
const router = useRouter()

const id = route.params.id as string
const playlist = ref<PlaylistResponse | null>(null)
const nominations = ref<NominationResponse[]>([])
const songMap = ref<Record<string, { name: string; artist: string }>>({})
const contributorMap = ref<Record<string, { name: string; avatarUrl: string | null }>>({})
const loading = ref(true)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const { data } = await api.playlists().get(id)
    playlist.value = data
    if (data.status === PlaylistStatus.Open || data.status === PlaylistStatus.Published) {
      await loadNominations()
    }
  } catch {
    error.value = 'Playlist not found.'
  } finally {
    loading.value = false
  }
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
        songMap.value[sid] = { name: data.name!, artist: data.artist! }
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
  openForm.value = { contributorId: '', deadline: '' }
  openError.value = null
  openOpen.value = true
}

async function submitOpen() {
  if (!openForm.value.contributorId.trim()) { openError.value = 'Contributor ID is required.'; return }
  if (!openForm.value.deadline) { openError.value = 'Deadline is required.'; return }
  opening.value = true
  openError.value = null
  try {
    const { data } = await api.playlists().open(id, {
      contributorId: openForm.value.contributorId.trim(),
      deadline: new Date(openForm.value.deadline).toISOString(),
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
const nominateForm = ref({ contributorId: '', artist: '', name: '', album: '', releaseYear: '' })
const nominateError = ref<string | null>(null)
const nominating = ref(false)
const allContributors = ref<Array<{ id: string; name: string; avatarUrl: string | null }>>([])

const eligibleContributors = computed(() => {
  const nominated = new Set(nominations.value.map(n => n.nominatedById).filter(Boolean))
  return allContributors.value.filter(c => !nominated.has(c.id))
})

function showNominate(contributorId = '') {
  nominateForm.value = { contributorId, artist: '', name: '', album: '', releaseYear: '' }
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
  } catch (e: unknown) {
    publishError.value = extractDetail(e) ?? 'Failed to publish playlist.'
  } finally {
    publishing.value = false
  }
}

// ── helpers ───────────────────────────────────────────────────────────────────

function extractDetail(e: unknown): string | null {
  if (e && typeof e === 'object' && 'response' in e) {
    const r = (e as { response?: { data?: { detail?: string } } }).response
    return r?.data?.detail ?? null
  }
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
        <Button v-if="playlist?.status === PlaylistStatus.New" variant="outline" size="sm" @click="showOpen">
          <Play class="h-4 w-4" />
          Open for nominations
        </Button>
        <Button v-if="playlist?.status === PlaylistStatus.Open && deadlinePassed" variant="outline" size="sm" @click="showPublish">
          <BookOpen class="h-4 w-4" />
          Publish
        </Button>
        <Button v-if="playlist" variant="outline" size="sm" @click="openEdit">
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
            <RouterLink v-if="playlist.leadContributorId" :to="`/contributors/${playlist.leadContributorId}`" class="underline underline-offset-2">
              {{ playlist.leadContributorId }}
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
      <section v-if="playlist.status === PlaylistStatus.Open || playlist.status === PlaylistStatus.Published">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-lg font-semibold">Nominations</h2>
          <Button v-if="playlist.status === PlaylistStatus.Open && !deadlinePassed" size="sm" @click="showNominate">
            <Send class="h-4 w-4" />
            Nominate song
          </Button>
        </div>

        <div v-if="nominations.length === 0" class="text-sm text-muted-foreground py-4 text-center border border-border rounded-md">
          No nominations yet.
        </div>

        <div v-else class="divide-y divide-border rounded-md border border-border overflow-hidden [&>div:nth-child(even)]:bg-muted/40">
          <div v-for="nom in nominations" :key="nom.id" class="grid grid-cols-3 items-center px-4 py-3 gap-4 text-sm">
            <!-- Song -->
            <div class="min-w-0">
              <div class="font-medium truncate">{{ songMap[nom.songId!]?.name ?? nom.songId }}</div>
              <div class="text-muted-foreground text-xs truncate">{{ songMap[nom.songId!]?.artist }}</div>
            </div>
            <!-- Contributor -->
            <div class="flex items-center gap-2 min-w-0 text-xs text-muted-foreground">
              <img v-if="contributorMap[nom.nominatedById!]?.avatarUrl" :src="contributorMap[nom.nominatedById!].avatarUrl!" class="w-5 h-5 rounded-full object-cover shrink-0" :alt="contributorMap[nom.nominatedById!].name" />
              <span class="truncate">{{ contributorMap[nom.nominatedById!]?.name ?? nom.nominatedById }}</span>
            </div>
            <!-- Status / actions -->
            <div class="flex items-center justify-end gap-1">
              <template v-if="playlist.status === PlaylistStatus.Open">
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
          <Label for="open-contributor">Lead contributor ID <span class="text-destructive">*</span></Label>
          <Input id="open-contributor" v-model="openForm.contributorId" placeholder="cont-…" />
        </div>
        <div class="space-y-1.5">
          <Label for="open-deadline">Nomination deadline <span class="text-destructive">*</span></Label>
          <Input id="open-deadline" v-model="openForm.deadline" type="datetime-local" />
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
          <Select v-model="nominateForm.contributorId" placeholder="Select contributor…">
            <SelectItem
              v-for="c in eligibleContributors" :key="c.id" :value="c.id"
              class="relative flex w-full cursor-default select-none items-center gap-2 rounded-sm py-1.5 pl-2 pr-8 text-sm outline-none focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
            >
              <span class="absolute right-2 flex h-3.5 w-3.5 items-center justify-center">
                <SelectItemIndicator><Check class="h-4 w-4" /></SelectItemIndicator>
              </span>
              <img v-if="c.avatarUrl" :src="c.avatarUrl" :alt="c.name" class="w-6 h-6 rounded-full object-cover shrink-0" />
              <div v-else class="w-6 h-6 rounded-full bg-muted shrink-0" />
              <SelectItemText>{{ c.name }}</SelectItemText>
            </SelectItem>
          </Select>
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
          <Label for="pub-id">Your contributor ID <span class="text-destructive">*</span></Label>
          <Input id="pub-id" v-model="publishContributorId" placeholder="cont-…" autofocus />
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
