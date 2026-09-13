<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { type ContributorResponse, type PlaylistResponse } from '@orgasm/backend-client'
import { api } from '@/api'
import { ArrowLeft, Pencil } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PlaylistStatusBadge from '@/components/PlaylistStatusBadge.vue'

const route = useRoute()
const router = useRouter()
const { t, d } = useI18n()

const id = route.params.id as string
const contributor = ref<ContributorResponse | null>(null)
const playlists = ref<PlaylistResponse[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const [contRes, plRes] = await Promise.all([
      api.contributors().get(id),
      api.contributors().playlists(id),
    ])
    contributor.value = contRes.data
    playlists.value = plRes.data.content ?? []
  } catch {
    error.value = t('contributors.notFound')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function formatDate(iso: string | null | undefined) {
  if (!iso) return '—'
  return d(new Date(iso), 'dateTime')
}

// ── Edit dialog ───────────────────────────────────────────────────────────────

const dialogOpen = ref(false)
const form = ref({ name: '', email: '', avatarUrl: '' })
const formError = ref<string | null>(null)
const saving = ref(false)

function openEdit() {
  form.value = {
    name: contributor.value!.name!,
    email: contributor.value!.email ?? '',
    avatarUrl: contributor.value!.avatarUrl ?? '',
  }
  formError.value = null
  dialogOpen.value = true
}

async function submitEdit() {
  if (!form.value.name.trim()) {
    formError.value = t('common.nameRequired')
    return
  }
  saving.value = true
  formError.value = null
  try {
    const { data } = await api.contributors().update(id, {
      name: form.value.name.trim(),
      email: form.value.email.trim() || undefined,
      avatarUrl: form.value.avatarUrl.trim() || undefined,
    })
    contributor.value = data
    dialogOpen.value = false
  } catch {
    formError.value = t('common.saveFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-6 max-w-2xl">

    <div class="flex items-center gap-3">
      <Button variant="ghost" size="icon" @click="router.back()">
        <ArrowLeft class="h-4 w-4" />
      </Button>
      <template v-if="contributor?.avatarUrl">
        <img :src="contributor.avatarUrl" :alt="contributor.name ?? ''" class="h-10 w-10 rounded-full object-cover shrink-0" />
      </template>
      <div v-else-if="contributor" class="h-10 w-10 rounded-full bg-muted flex items-center justify-center text-sm text-muted-foreground font-medium shrink-0">
        {{ (contributor.name ?? '?')[0].toUpperCase() }}
      </div>
      <h1 class="text-2xl font-semibold">
        <span v-if="loading" class="inline-block w-48 h-7 rounded bg-muted animate-pulse" />
        <span v-else>{{ contributor?.name }}</span>
      </h1>
      <Button v-if="contributor" variant="outline" size="sm" class="ml-auto" @click="openEdit">
        <Pencil class="h-4 w-4" />
        {{ t('common.edit') }}
      </Button>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <template v-if="contributor">
      <dl class="divide-y divide-border rounded-md border border-border text-sm overflow-hidden [&>div:nth-child(even)]:bg-muted/40">
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">{{ t('common.id') }}</dt>
          <dd>{{ contributor.id }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">{{ t('common.name') }}</dt>
          <dd class="font-medium">{{ contributor.name }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">{{ t('common.email') }}</dt>
          <dd class="text-muted-foreground">{{ contributor.email ?? '—' }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">{{ t('common.avatarUrl') }}</dt>
          <dd class="text-muted-foreground truncate">
            <a v-if="contributor.avatarUrl" :href="contributor.avatarUrl" target="_blank" rel="noopener" class="underline underline-offset-2">{{ contributor.avatarUrl }}</a>
            <span v-else>—</span>
          </dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">{{ t('common.created') }}</dt>
          <dd>{{ formatDate(contributor.createdAt) }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">{{ t('common.updated') }}</dt>
          <dd>{{ formatDate(contributor.updatedAt) }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">{{ t('common.version') }}</dt>
          <dd class="text-muted-foreground">{{ contributor.version }}</dd>
        </div>
      </dl>

      <!-- Playlists section -->
      <section>
        <h2 class="text-lg font-semibold mb-3">{{ t('contributors.ledPlaylists') }}</h2>
        <div v-if="playlists.length === 0" class="text-sm text-muted-foreground py-4 text-center border border-border rounded-md">
          {{ t('contributors.noLedPlaylists') }}
        </div>
        <div v-else class="divide-y divide-border rounded-md border border-border overflow-hidden [&>a:nth-child(even)]:bg-muted/40">
          <RouterLink
            v-for="pl in playlists"
            :key="pl.id"
            :to="`/playlists/${pl.id}`"
            class="flex items-center px-4 py-3 gap-4 text-sm hover:bg-muted/50 transition-colors"
          >
            <div class="flex-1 min-w-0">
              <div class="font-medium truncate">{{ pl.name }}</div>
              <div v-if="pl.description" class="text-muted-foreground text-xs truncate">{{ pl.description }}</div>
            </div>
            <PlaylistStatusBadge v-if="pl.status" :status="pl.status" />
          </RouterLink>
        </div>
      </section>
    </template>

  </div>

  <!-- Edit dialog -->
  <Dialog v-model:open="dialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ t('contributors.editTitle') }}</DialogTitle>
      </DialogHeader>
      <form class="space-y-4" @submit.prevent="submitEdit">
        <div class="space-y-1.5">
          <Label for="name">{{ t('common.name') }} <span class="text-destructive">*</span></Label>
          <Input id="name" v-model="form.name" :placeholder="t('fields.fullName')" autofocus />
        </div>
        <div class="space-y-1.5">
          <Label for="email">{{ t('common.email') }}</Label>
          <Input id="email" v-model="form.email" :placeholder="t('fields.emailPlaceholder')" type="email" />
        </div>
        <div class="space-y-1.5">
          <Label for="avatarUrl">{{ t('common.avatarUrl') }}</Label>
          <Input id="avatarUrl" v-model="form.avatarUrl" :placeholder="t('fields.avatarUrlPlaceholder')" type="url" />
        </div>
        <p v-if="formError" class="text-sm text-destructive">{{ formError }}</p>
      </form>
      <DialogFooter>
        <Button variant="outline" :disabled="saving" @click="dialogOpen = false">{{ t('common.cancel') }}</Button>
        <Button :disabled="saving" @click="submitEdit">{{ saving ? t('common.saving') : t('common.save') }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
