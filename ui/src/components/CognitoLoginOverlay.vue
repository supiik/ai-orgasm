<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import '@/amplify'
import { Authenticator, useAuthenticator } from '@aws-amplify/ui-vue'
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

const authStore = useAuthStore()
const router = useRouter()

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
    linkError.value = e instanceof Error ? e.message : 'Failed to check linked account'
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
    linkError.value = e instanceof Error ? e.message : 'Failed to link account'
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
              <h1 class="text-xl font-semibold tracking-wide">ORGAnized Spotify Mediabuilding</h1>
              <p class="text-sm text-muted-foreground">Signed in as {{ user?.signInDetails?.loginId ?? user?.username }}</p>
            </div>

            <div v-if="checkingLink" class="h-16 rounded-lg bg-muted animate-pulse" />

            <div v-else-if="contributor" class="space-y-4 text-center">
              <p class="text-sm">
                Linked to contributor <span class="font-medium">{{ contributor.name }}</span>
              </p>
              <Button variant="outline" class="w-full" @click="signOut">Sign out</Button>
            </div>

            <form v-else class="space-y-4" @submit.prevent="completeLink">
              <p class="text-sm text-muted-foreground">
                Complete your profile to link this account to a contributor.
              </p>
              <div class="space-y-1">
                <Label for="link-org">Organization</Label>
                <Select id="link-org" v-model="linkOrgSlug" placeholder="Select organization…">
                  <SelectItem v-for="org in organizations" :key="org.slug" :value="org.slug">
                    {{ org.name }}
                  </SelectItem>
                </Select>
              </div>
              <div class="space-y-1">
                <Label for="link-name">Name</Label>
                <Input id="link-name" v-model="linkName" placeholder="Your name" required />
              </div>
              <p v-if="linkError" class="text-sm text-destructive">{{ linkError }}</p>
              <Button type="submit" class="w-full" :disabled="linking || !linkName.trim() || !linkOrgSlug">
                {{ linking ? 'Linking…' : 'Continue' }}
              </Button>
              <Button variant="ghost" class="w-full" type="button" @click="signOut">Sign out</Button>
            </form>
          </div>
        </template>
      </authenticator>
    </div>
  </div>
</template>
