<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { AlertTriangle } from 'lucide-vue-next'
import { api } from '@/api'
import type { LicenseStatus } from '@/api-backend'

// Shown to every member when licence enforcement is on and the organization is not in the
// "active" state. Failure to load (e.g. the Spring backend, which has no licence endpoint) hides it.
const { t, d } = useI18n()
const status = ref<LicenseStatus | null>(null)

const day = (s?: string) => (s ? d(new Date(`${s}T00:00:00`), 'date') : '')

onMounted(async () => {
  try {
    status.value = (await api.organization().getLicense()).data
  } catch {
    status.value = null
  }
})
</script>

<template>
  <div
    v-if="status?.enforced && status.state !== 'active'"
    role="status"
    class="mb-4 flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-100"
  >
    <AlertTriangle class="h-4 w-4 shrink-0 mt-0.5" />
    <p v-if="status.state === 'grace'">{{ t('organization.bannerGrace', { date: day(status.validUntil), until: day(status.graceUntil) }) }}</p>
    <p v-else-if="status.state === 'expired'">{{ t('organization.bannerExpired') }}</p>
    <p v-else>{{ t('organization.bannerNone') }}</p>
  </div>
</template>
