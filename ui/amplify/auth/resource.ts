import { defineAuth } from '@aws-amplify/backend'

// Cognito User Pool for the Lambda API — additive to, not a replacement for, the traditional
// backend's Keycloak auth (unchanged). Email as username + auto-verified email, public app
// client (no secret) — see CLAUDE.md "Cognito auth for the Lambda API".
export const auth = defineAuth({
  loginWith: {
    email: true,
  },
})
