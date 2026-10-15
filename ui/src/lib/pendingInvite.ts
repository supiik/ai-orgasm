// A `?token=...` query param captured by App.vue (which always mounts, even while a login
// overlay is up — see its `loginRequired` gate) and stashed here so MockLoginOverlay /
// CognitoLoginOverlay can pick it up once they mount, pre-fill "You're joining <org>", and send
// it as `inviteToken` instead of `organizationSlug` on register/link.
const KEY = 'pending-invite-token'

export function getPendingInviteToken(): string | null {
  try {
    return sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function setPendingInviteToken(token: string): void {
  try {
    sessionStorage.setItem(KEY, token)
  } catch {
    // Private browsing / blocked storage — the picker just falls back to the org-slug list.
  }
}

export function clearPendingInviteToken(): void {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // ignore
  }
}
