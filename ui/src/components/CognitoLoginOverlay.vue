<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import '@/amplify'
import { Authenticator, useAuthenticator, translations } from '@aws-amplify/ui-vue'
import { I18n } from 'aws-amplify/utils'
import '@aws-amplify/ui-vue/styles.css'
import { getCurrentContributor, linkContributor, listOrganizations, type LambdaContributorResponse, type OrganizationResponse } from '@/lambdaApi'
import { useAuthStore } from '@/stores/auth'
import { Select, SelectItem } from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'

// Cognito's sign-up/sign-in UI + post-signup Contributor-linking flow. Rendered by App.vue when
// authMode === 'cognito' and no Contributor is linked yet (see CLAUDE.md "TypeScript Lambda API
// via Amplify Gen 2").

const { t, locale } = useI18n()
const authStore = useAuthStore()
const router = useRouter()

// <authenticator>'s own strings (sign-in/sign-up/confirm-code) come from Amplify UI's bundled
// dictionaries, keyed by the same language codes we use; a language it doesn't ship (e.g. `sk`)
// silently falls back to English inside the widget, our surrounding text still translates.
I18n.putVocabularies(translations)
watch(locale, (l) => I18n.setLanguage(l), { immediate: true })

const contributor = ref<LambdaContributorResponse | null>(null)
const checkingLink = ref(false)
const linkError = ref<string | null>(null)

const organizations = ref<OrganizationResponse[]>([])
const linkName = ref('')
const linkOrgSlug = ref('')
const linking = ref(false)

// useAuthenticator() must not be called before <authenticator> itself has mounted and run its
// own init — calling it earlier steals the first subscription to the shared auth state machine,
// and it never receives its INIT event (the state machine gets stuck in "setup" forever, and
// <authenticator> renders nothing). Deferring to onMounted lets <authenticator> (rendered
// unconditionally in the template below, so it always mounts before this runs) initialize first.
// See https://github.com/aws-amplify/amplify-ui/issues/5028.
onMounted(() => {
  const authenticator = useAuthenticator()
  watch(
    () => authenticator.authStatus,
    (status) => {
      if (status === 'authenticated') checkLinkedContributor()
      else contributor.value = null
    },
    { immediate: true },
  )
})

onMounted(async () => {
  try {
    organizations.value = await listOrganizations()
  } catch {
    // Org picker just stays empty; the link form's submit button is disabled without a selection.
  }
})

function onContributorResolved(c: LambdaContributorResponse) {
  contributor.value = c
  authStore.setCognitoContributor(c)
}

async function checkLinkedContributor() {
  checkingLink.value = true
  linkError.value = null
  try {
    const found = await getCurrentContributor()
    if (found) onContributorResolved(found)
    else contributor.value = null
  } catch (e) {
    linkError.value = e instanceof Error ? e.message : t('login.checkLinkFailed')
  } finally {
    checkingLink.value = false
  }
}

async function completeLink() {
  if (!linkName.value.trim() || !linkOrgSlug.value) return
  linking.value = true
  linkError.value = null
  try {
    const linked = await linkContributor({
      organizationSlug: linkOrgSlug.value,
      name: linkName.value,
    })
    onContributorResolved(linked)
    router.push({ name: 'home' })
  } catch (e) {
    linkError.value = e instanceof Error ? e.message : t('login.linkFailed')
  } finally {
    linking.value = false
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-background overflow-y-auto py-10">
    <div class="w-full max-w-lg px-6 space-y-6 text-center">
      <authenticator>
        <template v-slot="{ user, signOut }">
          <div class="space-y-6 text-left">
            <div class="text-center space-y-1">
              <h1 class="text-xl font-semibold tracking-wide">{{ t('app.title') }}</h1>
              <p class="text-sm text-muted-foreground">{{ t('login.signedInAs', { user: user?.signInDetails?.loginId ?? user?.username }) }}</p>
            </div>

            <div v-if="checkingLink" class="h-16 rounded-lg bg-muted animate-pulse" />

            <div v-else-if="contributor" class="space-y-4 text-center">
              <p class="text-sm">
                {{ t('login.linkedTo') }} <span class="font-medium">{{ contributor.name }}</span>
              </p>
              <Button variant="outline" class="w-full" @click="signOut">{{ t('login.signOut') }}</Button>
            </div>

            <form v-else class="space-y-4" @submit.prevent="completeLink">
              <p class="text-sm text-muted-foreground">
                {{ t('login.completeProfile') }}
              </p>
              <div class="space-y-1">
                <Label for="link-org">{{ t('login.organization') }}</Label>
                <Select id="link-org" v-model="linkOrgSlug" :placeholder="t('login.selectOrganization')">
                  <SelectItem v-for="org in organizations" :key="org.slug" :value="org.slug">
                    {{ org.name }}
                  </SelectItem>
                </Select>
              </div>
              <div class="space-y-1">
                <Label for="link-name">{{ t('common.name') }}</Label>
                <Input id="link-name" v-model="linkName" :placeholder="t('login.yourName')" required />
              </div>
              <p v-if="linkError" class="text-sm text-destructive">{{ linkError }}</p>
              <Button type="submit" class="w-full" :disabled="linking || !linkName.trim() || !linkOrgSlug">
                {{ linking ? t('login.linking') : t('login.continue') }}
              </Button>
              <Button variant="ghost" class="w-full" type="button" @click="signOut">{{ t('login.signOut') }}</Button>
            </form>
          </div>
        </template>
      </authenticator>
    </div>
  </div>
</template>
