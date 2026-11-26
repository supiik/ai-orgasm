<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { type ContributorPage, type ContributorResponse as BaseContributorResponse } from '@orgasm/backend-client'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { KeyRound, Link, Copy, Check, RefreshCw, Ban, ShieldOff, ShieldCheck, Shield } from 'lucide-vue-next'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { LicenseStatus } from '@/api-backend'

// `isOrgAdmin` isn't in the generated SDK type (see CLAUDE.md "Updating the spec") but is
// present on every contributor the real/mock backends return.
type ContributorResponse = BaseContributorResponse & { isOrgAdmin?: boolean }

const { t, d } = useI18n()
const authStore = useAuthStore()

// ── Invite link ───────────────────────────────────────────────────────────────

const inviteToken = ref<string | null>(null)
const linkLoading = ref(false)
const linkError = ref<string | null>(null)
const copied = ref(false)

const inviteUrl = computed(() => inviteToken.value ? `${window.location.origin}/join?token=${inviteToken.value}` : null)

async function fetchInviteLink() {
  linkLoading.value = true
  linkError.value = null
  try {
    const { data } = await api.organization().getInviteLink()
    inviteToken.value = data.inviteToken
  } catch {
    linkError.value = t('organization.linkLoadFailed')
  } finally {
    linkLoading.value = false
  }
}

async function regenerateLink() {
  linkLoading.value = true
  linkError.value = null
  try {
    const { data } = await api.organization().createInviteLink()
    inviteToken.value = data.inviteToken
  } catch {
    linkError.value = t('organization.linkSaveFailed')
  } finally {
    linkLoading.value = false
  }
}

async function revokeLink() {
  linkLoading.value = true
  linkError.value = null
  try {
    await api.organization().revokeInviteLink()
    inviteToken.value = null
  } catch {
    linkError.value = t('organization.linkSaveFailed')
  } finally {
    linkLoading.value = false
  }
}

async function copyLink() {
  if (!inviteUrl.value) return
  await navigator.clipboard.writeText(inviteUrl.value)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

// ── Licence ───────────────────────────────────────────────────────────────────

const license = ref<LicenseStatus | null>(null)
const licenseError = ref<string | null>(null)
const licenseMessage = ref<string | null>(null)
const licenseKey = ref('')
const activating = ref(false)

const day = (s?: string) => (s ? d(new Date(`${s}T00:00:00`), 'date') : '')

async function fetchLicense() {
  licenseError.value = null
  try {
    license.value = (await api.organization().getLicense()).data
  } catch {
    licenseError.value = t('organization.licenseLoadFailed')
  }
}

async function activateLicense() {
  if (!licenseKey.value.trim()) return
  activating.value = true
  licenseError.value = null
  licenseMessage.value = null
  try {
    license.value = (await api.organization().activateLicense(licenseKey.value)).data
    licenseKey.value = ''
    licenseMessage.value = t('organization.activated')
  } catch (e) {
    licenseError.value = t('organization.activateFailed', { reason: (e as Error).message })
  } finally {
    activating.value = false
  }
}

// ── Members ───────────────────────────────────────────────────────────────────

const members = ref<ContributorResponse[]>([])
const membersLoading = ref(false)
const membersError = ref<string | null>(null)
const promotingId = ref<string | null>(null)

async function fetchMembers() {
  membersLoading.value = true
  membersError.value = null
  try {
    const { data } = await api.contributors().list(0, 50, 'name') as { data: ContributorPage }
    members.value = (data.content ?? []) as ContributorResponse[]
  } catch {
    membersError.value = t('organization.membersLoadFailed')
  } finally {
    membersLoading.value = false
  }
}

async function toggleOrgAdmin(member: ContributorResponse) {
  promotingId.value = member.id!
  try {
    await api.contributors().setOrgAdmin(member.id!, !member.isOrgAdmin)
    await fetchMembers()
  } catch {
    membersError.value = t('organization.membersLoadFailed')
  } finally {
    promotingId.value = null
  }
}

onMounted(() => {
  if (authStore.isOrgAdmin) {
    fetchLicense()
    fetchInviteLink()
    fetchMembers()
  }
})
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">{{ t('organization.title') }}</h1>

    <div v-if="!authStore.isOrgAdmin" class="flex items-start gap-3 rounded-md border border-border p-4 text-sm text-muted-foreground">
      <ShieldOff class="h-5 w-5 shrink-0" />
      <p>{{ t('organization.forbidden') }}</p>
    </div>

    <template v-else>
      <!-- Licence -->
      <section class="space-y-2" aria-labelledby="license-heading">
        <h2 id="license-heading" class="text-lg font-medium">{{ t('organization.license') }}</h2>
        <p class="text-sm text-muted-foreground">{{ t('organization.licenseHint') }}</p>
        <div v-if="licenseError" role="alert" class="text-sm text-destructive">{{ licenseError }}</div>
        <div v-if="licenseMessage" role="status" class="text-sm">{{ licenseMessage }}</div>
        <div v-if="license" class="flex items-start gap-2 rounded-md border border-border p-3 text-sm" data-testid="license-status">
          <KeyRound class="h-4 w-4 shrink-0 mt-0.5 text-muted-foreground" />
          <div class="space-y-1">
            <p v-if="license.state === 'active'">{{ t('organization.licenseActive', { date: day(license.validUntil) }) }}</p>
            <p v-else-if="license.state === 'grace'" class="text-amber-700 dark:text-amber-300">
              {{ t('organization.licenseGrace', { date: day(license.validUntil), until: day(license.graceUntil) }) }}
            </p>
            <p v-else-if="license.state === 'expired'" class="text-destructive">{{ t('organization.licenseExpired', { date: day(license.validUntil) }) }}</p>
            <p v-else>{{ t('organization.licenseNone') }}</p>
            <p v-if="license.maxMembers" class="text-muted-foreground">
              {{ t('organization.licenseMembers', { members: license.members, max: license.maxMembers }) }}
            </p>
          </div>
        </div>
        <form class="flex items-center gap-2" @submit.prevent="activateLicense">
          <label for="license-key" class="sr-only">{{ t('organization.licenseKey') }}</label>
          <Input
            id="license-key"
            v-model="licenseKey"
            class="max-w-xs font-mono"
            autocomplete="off"
            spellcheck="false"
            :placeholder="t('organization.licenseKeyPlaceholder')"
          />
          <Button type="submit" variant="outline" size="sm" :disabled="activating || !licenseKey.trim()">
            {{ t('organization.activate') }}
          </Button>
        </form>
      </section>

      <!-- Invite link -->
      <section class="space-y-2">
        <h2 class="text-lg font-medium">{{ t('organization.inviteLink') }}</h2>
        <p class="text-sm text-muted-foreground">{{ t('organization.inviteLinkHint') }}</p>
        <div v-if="linkError" class="text-sm text-destructive">{{ linkError }}</div>

        <div v-if="inviteUrl" class="flex items-center gap-2 rounded-md border border-border p-3 text-sm">
          <Link class="h-4 w-4 shrink-0 text-muted-foreground" />
          <span class="flex-1 truncate font-mono">{{ inviteUrl }}</span>
          <Button variant="outline" size="sm" :disabled="linkLoading" @click="copyLink">
            <Check v-if="copied" class="h-4 w-4" />
            <Copy v-else class="h-4 w-4" />
            {{ copied ? t('organization.copied') : t('organization.copy') }}
          </Button>
          <Button variant="outline" size="sm" :disabled="linkLoading" @click="regenerateLink">
            <RefreshCw class="h-4 w-4" />
            {{ t('organization.regenerate') }}
          </Button>
          <Button variant="outline" size="sm" :disabled="linkLoading" @click="revokeLink">
            <Ban class="h-4 w-4" />
            {{ t('organization.revoke') }}
          </Button>
        </div>
        <div v-else class="flex items-center gap-2">
          <p class="text-sm text-muted-foreground">{{ t('organization.noLink') }}</p>
          <Button variant="outline" size="sm" :disabled="linkLoading" @click="regenerateLink">
            {{ t('organization.generate') }}
          </Button>
        </div>
      </section>

      <!-- Members -->
      <section class="space-y-2">
        <h2 class="text-lg font-medium">{{ t('organization.members') }}</h2>
        <div v-if="membersError" class="text-sm text-destructive">{{ membersError }}</div>
        <div class="rounded-md border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{{ t('common.name') }}</TableHead>
                <TableHead>{{ t('common.email') }}</TableHead>
                <TableHead class="w-44">{{ t('organization.role') }}</TableHead>
                <TableHead class="w-12" />
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
                      v-if="member.isOrgAdmin"
                      class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200"
                    >
                      <ShieldCheck class="h-3 w-3" /> {{ t('organization.orgAdmin') }}
                    </span>
                    <span v-else class="text-muted-foreground text-xs">{{ t('organization.member') }}</span>
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon"
                      :disabled="promotingId === member.id"
                      :aria-label="member.isOrgAdmin ? t('organization.demote') : t('organization.promote')"
                      :title="member.isOrgAdmin ? t('organization.demote') : t('organization.promote')"
                      @click="toggleOrgAdmin(member)"
                    >
                      <Shield class="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              </template>
              <template v-else>
                <TableRow>
                  <TableCell colspan="4" class="text-center text-muted-foreground py-10">
                    {{ t('organization.membersEmpty') }}
                  </TableCell>
                </TableRow>
              </template>
            </TableBody>
          </Table>
        </div>
      </section>
    </template>
  </div>
</template>
