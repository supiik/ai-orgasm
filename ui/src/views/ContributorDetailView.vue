<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Configuration, ContributorsApi, type ContributorResponse } from '@orgasm/backend-client'
import { ArrowLeft, Pencil } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const route = useRoute()
const router = useRouter()
const api = new ContributorsApi(new Configuration({ basePath: '' }))

const id = Number(route.params.id)
const contributor = ref<ContributorResponse | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const { data } = await api.findContributorById(id)
    contributor.value = data
  } catch {
    error.value = 'Contributor not found.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function formatDate(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
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
    formError.value = 'Name is required.'
    return
  }
  saving.value = true
  formError.value = null
  try {
    const { data } = await api.updateContributor(id, {
      name: form.value.name.trim(),
      email: form.value.email.trim() || undefined,
      avatarUrl: form.value.avatarUrl.trim() || undefined,
    })
    contributor.value = data
    dialogOpen.value = false
  } catch {
    formError.value = 'Failed to save. Please try again.'
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
        Edit
      </Button>
    </div>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <template v-if="contributor">
      <dl class="divide-y divide-border rounded-md border border-border text-sm">
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">ID</dt>
          <dd>{{ contributor.id }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Name</dt>
          <dd class="font-medium">{{ contributor.name }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Email</dt>
          <dd class="text-muted-foreground">{{ contributor.email ?? '—' }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Avatar URL</dt>
          <dd class="text-muted-foreground truncate">
            <a v-if="contributor.avatarUrl" :href="contributor.avatarUrl" target="_blank" rel="noopener" class="underline underline-offset-2">{{ contributor.avatarUrl }}</a>
            <span v-else>—</span>
          </dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Created</dt>
          <dd>{{ formatDate(contributor.createdAt!) }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Updated</dt>
          <dd>{{ formatDate(contributor.updatedAt!) }}</dd>
        </div>
        <div class="flex px-4 py-3 gap-4">
          <dt class="w-32 shrink-0 text-muted-foreground">Version</dt>
          <dd class="text-muted-foreground">{{ contributor.version }}</dd>
        </div>
      </dl>
    </template>

  </div>

  <!-- Edit dialog -->
  <Dialog v-model:open="dialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Edit contributor</DialogTitle>
      </DialogHeader>
      <form class="space-y-4" @submit.prevent="submitEdit">
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
        <Button :disabled="saving" @click="submitEdit">{{ saving ? 'Saving…' : 'Save' }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
