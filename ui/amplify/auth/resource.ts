import { defineAuth } from '@aws-amplify/backend'

// Cognito User Pool for the Lambda API — additive to, not a replacement for, the traditional
// backend's Keycloak auth (unchanged). Email as username + auto-verified email, public app
// client (no secret) — see CLAUDE.md "Cognito auth for the Lambda API".
export const auth = defineAuth({
  loginWith: {
    email: true,
  },
  // Membership unlocks the admin-* functions (`admin` AuthMode, lib/http.ts) and the UI's /admin
  // section. Nobody is in it after a deploy — an operator adds the first admin by hand:
  //   aws cognito-idp admin-add-user-to-group --user-pool-id <id> --username <email> --group-name admins
  groups: ['admins'],
})
