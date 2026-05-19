<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type PlaylistResponse, type NominationResponse, PlaylistStatus, NominationStatus } from '@orgasm/backend-client'
import { api } from '@/api'
import { ArrowLeft, Pencil, Play, Send, CheckCircle, XCircle, BookOpen } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PlaylistStatusBadge from '@/components/PlaylistStatusBadge.vue'

const route = useRoute()
const router = useRouter()

const id = route.params.id as string
const playlist = ref<PlaylistResponse | null>(null)
const nominations = ref<NominationResponse[]>([])
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
  } catch {
    // non-critical
  }
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
const nominateForm = ref({ contributorId: '', songId: '' })
const nominateError = ref<string | null>(null)
const nominating = ref(false)

function showNominate() {
  nominateForm.value = { contributorId: '', songId: '' }
  nominateError.value = null
  nominateOpen.value = true
}

async function submitNominate() {
  if (!nominateForm.value.contributorId.trim() || !nominateForm.value.songId.trim()) {
    nominateError.value = 'All fields are required.'
    return
  }
  nominating.value = true
  nominateError.value = null
  try {
    await api.nominations().create(id, {
      contributorId: nominateForm.value.contributorId.trim(),
      songId: nominateForm.value.songId.trim(),
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

const reviewId = ref('')
const reviewOpen = ref(false)
const reviewError = ref<string | null>(null)
const reviewing = ref(false)
const pendingNomId = ref<string | null>(null)
const pendingAction = ref<'approve' | 'decline'>('approve')

function showReview(nomId: string, action: 'approve' | 'decline') {
  pendingNomId.value = nomId
  pendingAction.value = action
  reviewId.value = ''
  reviewError.value = null
  reviewOpen.value = true
}

async function submitReview() {
  if (!reviewId.value.trim()) { reviewError.value = 'Reviewer ID is required.'; return }
  reviewing.value = true
  reviewError.value = null
  try {
    const req = { reviewerId: reviewId.value.trim() }
    if (pendingAction.value === 'approve') {
      await api.nominations().approve(pendingNomId.value!, req)
    } else {
      await api.nominations().decline(pendingNomId.value!, req)
    }
    reviewOpen.value = false
    await loadNominations()
  } catch (e: unknown) {
    reviewError.value = extractDetail(e) ?? 'Failed to review nomination.'
  } finally {
    reviewing.value = false
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
      <dl class="divide-y divide-border rounded-md border border-border text-sm">
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

        <div v-else class="divide-y divide-border rounded-md border border-border">
          <div v-for="nom in nominations" :key="nom.id" class="flex items-center px-4 py-3 gap-4 text-sm">
            <div class="flex-1 min-w-0">
              <div class="font-medium truncate">{{ nom.songId }}</div>
              <div class="text-muted-foreground text-xs truncate">by {{ nom.nominatedById }}</div>
            </div>
            <span :class="['text-xs font-medium', statusClass(nom.status)]">{{ statusLabel(nom.status) }}</span>
            <template v-if="nom.status === NominationStatus.Pending && playlist.status === PlaylistStatus.Open">
              <Button size="sm" variant="ghost" class="text-green-600 hover:text-green-700" @click="showReview(nom.id!, 'approve')">
                <CheckCircle class="h-4 w-4" />
              </Button>
              <Button size="sm" variant="ghost" class="text-destructive hover:text-destructive/80" @click="showReview(nom.id!, 'decline')">
                <XCircle class="h-4 w-4" />
              </Button>
            </template>
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
          <Label for="nom-contributor">Your contributor ID <span class="text-destructive">*</span></Label>
          <Input id="nom-contributor" v-model="nominateForm.contributorId" placeholder="cont-…" />
        </div>
        <div class="space-y-1.5">
          <Label for="nom-song">Song ID <span class="text-destructive">*</span></Label>
          <Input id="nom-song" v-model="nominateForm.songId" placeholder="song-…" />
        </div>
        <p v-if="nominateError" class="text-sm text-destructive">{{ nominateError }}</p>
      </form>
      <DialogFooter>
        <Button variant="outline" :disabled="nominating" @click="nominateOpen = false">Cancel</Button>
        <Button :disabled="nominating" @click="submitNominate">{{ nominating ? 'Nominating…' : 'Nominate' }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Approve/Decline dialog -->
  <Dialog v-model:open="reviewOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ pendingAction === 'approve' ? 'Approve' : 'Decline' }} nomination</DialogTitle>
      </DialogHeader>
      <form class="space-y-4" @submit.prevent="submitReview">
        <div class="space-y-1.5">
          <Label for="review-id">Your contributor ID <span class="text-destructive">*</span></Label>
          <Input id="review-id" v-model="reviewId" placeholder="cont-…" autofocus />
        </div>
        <p v-if="reviewError" class="text-sm text-destructive">{{ reviewError }}</p>
      </form>
      <DialogFooter>
        <Button variant="outline" :disabled="reviewing" @click="reviewOpen = false">Cancel</Button>
        <Button :disabled="reviewing" :variant="pendingAction === 'decline' ? 'destructive' : 'default'" @click="submitReview">
          {{ reviewing ? 'Submitting…' : (pendingAction === 'approve' ? 'Approve' : 'Decline') }}
        </Button>
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
