<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ContributorResponse } from '@orgasm/backend-client'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { Select, SelectItem } from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import type { OrganizationResponse } from '@/mocks/handlers/organizations'

const { t } = useI18n()
const authStore = useAuthStore()
const activeTab = ref<'login' | 'register'>('login')

const contributors = ref<ContributorResponse[]>([])
const loading = ref(true)

const organizations = ref<OrganizationResponse[]>([])
const regName = ref('')
const regEmail = ref('')
const regOrgSlug = ref('')
const registering = ref(false)
const regError = ref<string | null>(null)

onMounted(async () => {
  try {
    const { data } = await api.contributors().list(0, 100)
    contributors.value = data.content ?? []
  } finally {
    loading.value = false
  }

  const res = await fetch('/api/v1/organizations')
  if (res.ok) {
    organizations.value = await res.json()
  }
})

function login(contributor: ContributorResponse) {
  authStore.mockLogin(contributor)
}

async function register() {
  if (!regName.value.trim() || !regOrgSlug.value) return
  registering.value = true
  regError.value = null
  try {
    const res = await fetch('/api/v1/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: regName.value,
        email: regEmail.value || undefined,
        organizationSlug: regOrgSlug.value,
      }),
    })
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      regError.value = body.detail ?? body.message ?? t('login.error', { status: res.status })
      return
    }
    const created: ContributorResponse = await res.json()
    authStore.mockLogin(created)
  } catch {
    regError.value = t('login.networkError')
  } finally {
    registering.value = false
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-background">
    <div class="w-full max-w-lg px-6 space-y-6 text-center">
      <div class="space-y-2">
        <h1 class="text-xl font-semibold tracking-wide">{{ t('app.title') }}</h1>
        <p class="text-sm text-muted-foreground">{{ t('login.subtitle') }}</p>
      </div>

      <div class="flex gap-4 border-b border-border">
        <button
          class="pb-2 px-1 text-sm font-medium transition-colors"
          :class="activeTab === 'login' ? 'border-b-2 border-primary text-foreground' : 'text-muted-foreground hover:text-foreground'"
          @click="activeTab = 'login'"
        >
          {{ t('login.selectIdentity') }}
        </button>
        <button
          class="pb-2 px-1 text-sm font-medium transition-colors"
          :class="activeTab === 'register' ? 'border-b-2 border-primary text-foreground' : 'text-muted-foreground hover:text-foreground'"
          @click="activeTab = 'register'"
        >
          {{ t('login.register') }}
        </button>
      </div>

      <template v-if="activeTab === 'login'">
        <div v-if="loading" class="space-y-3">
          <div v-for="i in 3" :key="i" class="h-16 rounded-lg bg-muted animate-pulse" />
        </div>

        <div v-else class="space-y-2">
          <button
            v-for="c in contributors"
            :key="c.id"
            class="flex w-full items-center gap-4 rounded-lg border border-border px-4 py-3 text-left transition-colors hover:bg-accent hover:text-accent-foreground"
            @click="login(c)"
          >
            <img
              v-if="c.avatarUrl"
              :src="c.avatarUrl"
              :alt="c.name"
              class="h-10 w-10 rounded-full object-cover shrink-0"
            />
            <div
              v-else
              class="h-10 w-10 rounded-full bg-muted flex items-center justify-center text-sm font-medium text-muted-foreground shrink-0"
            >
              {{ (c.name ?? '?')[0].toUpperCase() }}
            </div>
            <div class="min-w-0">
              <div class="font-medium truncate">{{ c.name }}</div>
              <div v-if="c.email" class="text-xs text-muted-foreground truncate">{{ c.email }}</div>
            </div>
          </button>
        </div>
      </template>

      <template v-else>
        <form class="space-y-4 text-left" @submit.prevent="register">
          <div class="space-y-1">
            <Label for="reg-org">{{ t('login.organization') }}</Label>
            <Select id="reg-org" v-model="regOrgSlug" :placeholder="t('login.selectOrganization')">
              <SelectItem v-for="org in organizations" :key="org.slug" :value="org.slug">
                {{ org.name }}
              </SelectItem>
            </Select>
          </div>
          <div class="space-y-1">
            <Label for="reg-name">{{ t('common.name') }}</Label>
            <Input id="reg-name" v-model="regName" :placeholder="t('login.yourName')" required />
          </div>
          <div class="space-y-1">
            <Label for="reg-email">{{ t('login.emailOptional') }}</Label>
            <Input id="reg-email" v-model="regEmail" type="email" :placeholder="t('login.emailPlaceholder')" />
          </div>
          <p v-if="regError" class="text-sm text-destructive">{{ regError }}</p>
          <Button
            type="submit"
            class="w-full"
            :disabled="registering || !regName.trim() || !regOrgSlug"
          >
            {{ registering ? t('login.registering') : t('login.register') }}
          </Button>
        </form>
      </template>
    </div>
  </div>
</template>
