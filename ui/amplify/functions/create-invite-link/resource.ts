import { defineFunction } from '@aws-amplify/backend'

export const createInviteLinkFn = defineFunction({
  name: 'create-invite-link',
  entry: './handler.ts',
})
