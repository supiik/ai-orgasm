<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, type AdminOrganization, type AdminContributor } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { Plus, Pencil, UserPlus, ShieldOff, Download } from 'lucide-vue-next'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

// Cross-tenant administration: organizations + manual membership. Rendered only for the Cognito
// `admins` group (SidebarNav link + router guard); the API rejects everyone else with 403
// regardless of what this view does, so the UI-side checks are convenience, not security.

const { t, d } = useI18n()
const authStore = useAuthStore()

// ── Organizations ────────────────────────────────────────────────────────────

const organizations = ref<AdminOrganization[]>([])
const orgsLoading = ref(false)
const orgsError = ref<string | null>(null)
const selectedOrgId = ref<number | null>(null)
const selectedOrg = computed(() => organizations.value.find(o => o.id === selectedOrgId.value) ?? null)

async function fetchOrganizations() {
  orgsLoading.value = true
  orgsError.value = null
  try {
    const { data } = await api.admin().listOrganizations()
    organizations.value = data
    if (selectedOrgId.value === null && data.length) selectedOrgId.value = data[0].id
  } catch {
    orgsError.value = t('admin.organizationsLoadFailed')
  } finally {
    orgsLoading.value = false
  }
}

onMounted(() => { if (authStore.isAdmin) fetchOrganizations() })

type OrgDialogMode = 'create' | 'edit'
const orgDialogOpen = ref(false)
const orgDialogMode = ref<OrgDialogMode>('create')
const editingOrgId = ref<number | null>(null)
const orgForm = ref({ slug: '', name: '', allowedDomain: '' })
const orgFormError = ref<string | null>(null)
const orgSaving = ref(false)

const orgDialogTitle = computed(() => orgDialogMode.value === 'create' ? t('admin.newOrganization') : t('admin.editOrganization'))
const orgSubmitLabel = computed(() => {
  if (orgSaving.value) return orgDialogMode.value === 'create' ? t('common.creating') : t('common.saving')
  return orgDialogMode.value === 'create' ? t('common.create') : t('common.save')
})

function openCreateOrg() {
  orgDialogMode.value = 'create'
  editingOrgId.value = null
  orgForm.value = { slug: '', name: '', allowedDomain: '' }
  orgFormError.value = null
  orgDialogOpen.value = true
}

function openEditOrg(org: AdminOrganization) {
  orgDialogMode.value = 'edit'
  editingOrgId.value = org.id
  orgForm.value = { slug: org.slug, name: org.name, allowedDomain: org.allowedDomain ?? '' }
  orgFormError.value = null
  orgDialogOpen.value = true
}

async function submitOrgForm() {
  if (!orgForm.value.name.trim()) {
    orgFormError.value = t('common.nameRequired')
    return
  }
  if (orgDialogMode.value === 'create' && !orgForm.value.slug.trim()) {
    orgFormError.value = t('admin.slugRequired')
    return
  }
  orgSaving.value = true
  orgFormError.value = null
  try {
    const name = orgForm.value.name.trim()
    const allowedDomain = orgForm.value.allowedDomain.trim() || undefined
    if (orgDialogMode.value === 'create') {
      const { data } = await api.admin().createOrganization({ slug: orgForm.value.slug.trim(), name, allowedDomain })
      selectedOrgId.value = data.id
    } else {
      await api.admin().updateOrganization(editingOrgId.value!, { name, allowedDomain })
    }
    await fetchOrganizations()
    orgDialogOpen.value = false
  } catch (e) {
    orgFormError.value = e instanceof Error ? e.message : String(e)
  } finally {
    orgSaving.value = false
  }
}

// ── Members of the selected organization ─────────────────────────────────────

const members = ref<AdminContributor[]>([])
const membersLoading = ref(false)
const membersError = ref<string | null>(null)

async function fetchMembers(orgId: number | null) {
  if (orgId === null) {
    members.value = []
    return
  }
  membersLoading.value = true
  membersError.value = null
  try {
    const { data } = await api.admin().listContributors(orgId)
    members.value = data
  } catch {
    membersError.value = t('admin.membersLoadFailed')
  } finally {
    membersLoading.value = false
  }
}

watch(selectedOrgId, fetchMembers, { immediate: true })

const memberDialogOpen = ref(false)
const memberForm = ref({ name: '', email: '' })
const memberFormError = ref<string | null>(null)
const memberSaving = ref(false)

function openAddMember() {
  memberForm.value = { name: '', email: '' }
  memberFormError.value = null
  memberDialogOpen.value = true
}

async function submitMemberForm() {
  if (!memberForm.value.name.trim()) {
    memberFormError.value = t('common.nameRequired')
    return
  }
  if (!memberForm.value.email.trim()) {
    memberFormError.value = t('admin.emailRequired')
    return
  }
  memberSaving.value = true
  memberFormError.value = null
  try {
    await api.admin().addContributor(selectedOrgId.value!, {
      name: memberForm.value.name.trim(),
      email: memberForm.value.email.trim(),
    })
    await fetchMembers(selectedOrgId.value)
    memberDialogOpen.value = false
  } catch (e) {
    memberFormError.value = e instanceof Error ? e.message : String(e)
  } finally {
    memberSaving.value = false
  }
}

// ── Export ──────────────────────────────────────────────────────────────────

const exporting = ref(false)
const exportError = ref<string | null>(null)

/**
 * Downloads the selected organization's complete data as one JSON file (a customer who is
 * leaving wants their whole history). The API assembles the document (admin-only,
 * `admin-export-organization`); the browser just saves it — no server-side storage involved.
 */
async function exportSelectedOrg() {
  if (!selectedOrg.value) return
  exporting.value = true
  exportError.value = null
  try {
    const { data } = await api.admin().exportOrganization(selectedOrg.value.id)
    const stamp = data.exportedAt.slice(0, 10)
    saveJsonFile(`${selectedOrg.value.slug}-export-${stamp}.json`, data)
  } catch (e) {
    exportError.value = e instanceof Error ? e.message : String(e)
  } finally {
    exporting.value = false
  }
}

function saveJsonFile(filename: string, payload: unknown) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

function formatDate(iso: string) {
  return d(new Date(iso), 'date')
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">{{ t('admin.title') }}</h1>
      <Button v-if="authStore.isAdmin" @click="openCreateOrg">
        <Plus class="h-4 w-4" />
        {{ t('admin.newOrganization') }}
      </Button>
    </div>

    <div v-if="!authStore.isAdmin" class="flex items-start gap-3 rounded-md border border-border p-4 text-sm text-muted-foreground">
      <ShieldOff class="h-5 w-5 shrink-0" />
      <p>{{ t('admin.forbidden') }}</p>
    </div>

    <template v-else>
      <!-- Organizations -->
      <section class="space-y-2">
        <h2 class="text-lg font-medium">{{ t('admin.organizations') }}</h2>
        <div v-if="orgsError" class="text-sm text-destructive">{{ orgsError }}</div>
        <div class="rounded-md border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead class="w-16">{{ t('common.id') }}</TableHead>
                <TableHead>{{ t('admin.slug') }}</TableHead>
                <TableHead>{{ t('common.name') }}</TableHead>
                <TableHead>{{ t('admin.allowedDomain') }}</TableHead>
                <TableHead class="w-12" />
              </TableRow>
            </TableHeader>
            <TableBody>
              <template v-if="orgsLoading">
                <TableRow v-for="i in 3" :key="i">
                  <TableCell colspan="5"><div class="h-4 rounded bg-muted animate-pulse" /></TableCell>
                </TableRow>
              </template>
              <template v-else-if="organizations.length">
                <TableRow
                  v-for="org in organizations"
                  :key="org.id"
                  class="cursor-pointer"
                  :class="{ 'bg-accent/50': org.id === selectedOrgId }"
                  :aria-selected="org.id === selectedOrgId"
                  @click="selectedOrgId = org.id"
                >
                  <TableCell class="text-muted-foreground">{{ org.id }}</TableCell>
                  <TableCell class="font-mono text-sm">{{ org.slug }}</TableCell>
                  <TableCell class="font-medium">{{ org.name }}</TableCell>
                  <TableCell class="text-muted-foreground">
                    <span v-if="org.allowedDomain">@{{ org.allowedDomain }}</span>
                    <span v-else class="italic">{{ t('admin.unrestricted') }}</span>
                  </TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" :aria-label="t('admin.editOrganization')" @click.stop="openEditOrg(org)">
                      <Pencil class="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              </template>
              <template v-else>
                <TableRow>
                  <TableCell colspan="5" class="text-center text-muted-foreground py-10">
                    {{ t('admin.organizationsEmpty') }}
                  </TableCell>
                </TableRow>
              </template>
            </TableBody>
          </Table>
        </div>
      </section>

      <!-- Members -->
      <section class="space-y-2">
        <div class="flex items-center justify-between gap-2">
          <h2 class="text-lg font-medium">
            {{ selectedOrg ? t('admin.membersOf', { name: selectedOrg.name }) : t('admin.members') }}
          </h2>
          <div v-if="selectedOrg" class="flex items-center gap-2">
            <Button variant="outline" :disabled="exporting" @click="exportSelectedOrg">
              <Download class="h-4 w-4" />
              {{ exporting ? t('admin.exporting') : t('admin.exportData') }}
            </Button>
            <Button variant="outline" @click="openAddMember">
              <UserPlus class="h-4 w-4" />
              {{ t('admin.addMember') }}
            </Button>
          </div>
        </div>
        <p v-if="!selectedOrg" class="text-sm text-muted-foreground">{{ t('admin.selectOrganization') }}</p>
        <template v-else>
          <p class="text-xs text-muted-foreground">{{ t('admin.exportHint') }}</p>
          <div v-if="exportError" class="text-sm text-destructive">{{ t('admin.exportFailed', { message: exportError }) }}</div>
          <div v-if="membersError" class="text-sm text-destructive">{{ membersError }}</div>
          <div class="rounded-md border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{{ t('common.name') }}</TableHead>
                  <TableHead>{{ t('common.email') }}</TableHead>
                  <TableHead class="w-40">{{ t('admin.status') }}</TableHead>
                  <TableHead class="w-36">{{ t('common.created') }}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <template v-if="membersLoading">
                  <TableRow v-for="i in 3" :key="i">
                    <TableCell colspan="4"><div class="h-4 rounded bg-muted animate-pulse" /></TableCell>
                  </TableRow>
                </template>
                <template v-else-if="members.length">
                  <TableRow v-for="member in members" :key="member.id">
                    <TableCell class="font-medium">{{ member.name }}</TableCell>
                    <TableCell class="text-muted-foreground">{{ member.email ?? '—' }}</TableCell>
                    <TableCell>
                      <span
                        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                        :class="member.linked
                          ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                          : 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200'"
                      >
                        {{ member.linked ? t('admin.linked') : t('admin.pending') }}
                      </span>
                    </TableCell>
                    <TableCell class="text-muted-foreground">{{ formatDate(member.createdAt!) }}</TableCell>
                  </TableRow>
                </template>
                <template v-else>
                  <TableRow>
                    <TableCell colspan="4" class="text-center text-muted-foreground py-10">
                      {{ t('admin.membersEmpty') }}
                    </TableCell>
                  </TableRow>
                </template>
              </TableBody>
            </Table>
          </div>
        </template>
      </section>
    </template>
  </div>

  <!-- Create / edit organization -->
  <Dialog v-model:open="orgDialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ orgDialogTitle }}</DialogTitle>
      </DialogHeader>

      <form class="space-y-4" @submit.prevent="submitOrgForm">
        <div class="space-y-1.5">
          <Label for="org-slug">{{ t('admin.slug') }} <span v-if="orgDialogMode === 'create'" class="text-destructive">*</span></Label>
          <Input
            id="org-slug"
            v-model="orgForm.slug"
            :placeholder="t('admin.slugPlaceholder')"
            :disabled="orgDialogMode === 'edit'"
            autocapitalize="off"
            spellcheck="false"
            autofocus
          />
          <p class="text-xs text-muted-foreground">{{ t('admin.slugHint') }}</p>
        </div>
        <div class="space-y-1.5">
          <Label for="org-name">{{ t('common.name') }} <span class="text-destructive">*</span></Label>
          <Input id="org-name" v-model="orgForm.name" />
        </div>
        <div class="space-y-1.5">
          <Label for="org-domain">{{ t('admin.allowedDomain') }}</Label>
          <Input id="org-domain" v-model="orgForm.allowedDomain" :placeholder="t('admin.allowedDomainPlaceholder')" autocapitalize="off" spellcheck="false" />
          <p class="text-xs text-muted-foreground">{{ t('admin.allowedDomainHint') }}</p>
        </div>
        <p v-if="orgFormError" class="text-sm text-destructive">{{ orgFormError }}</p>
      </form>

      <DialogFooter>
        <Button variant="outline" :disabled="orgSaving" @click="orgDialogOpen = false">{{ t('common.cancel') }}</Button>
        <Button :disabled="orgSaving" @click="submitOrgForm">{{ orgSubmitLabel }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Add member -->
  <Dialog v-model:open="memberDialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ selectedOrg ? t('admin.membersOf', { name: selectedOrg.name }) : t('admin.addMember') }}</DialogTitle>
      </DialogHeader>

      <form class="space-y-4" @submit.prevent="submitMemberForm">
        <p class="text-sm text-muted-foreground">{{ t('admin.addMemberHint') }}</p>
        <div class="space-y-1.5">
          <Label for="member-name">{{ t('common.name') }} <span class="text-destructive">*</span></Label>
          <Input id="member-name" v-model="memberForm.name" :placeholder="t('fields.fullName')" autofocus />
        </div>
        <div class="space-y-1.5">
          <Label for="member-email">{{ t('common.email') }} <span class="text-destructive">*</span></Label>
          <Input id="member-email" v-model="memberForm.email" :placeholder="t('fields.emailPlaceholder')" type="email" />
        </div>
        <p v-if="memberFormError" class="text-sm text-destructive">{{ memberFormError }}</p>
      </form>

      <DialogFooter>
        <Button variant="outline" :disabled="memberSaving" @click="memberDialogOpen = false">{{ t('common.cancel') }}</Button>
        <Button :disabled="memberSaving" @click="submitMemberForm">{{ memberSaving ? t('admin.adding') : t('admin.add') }}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
