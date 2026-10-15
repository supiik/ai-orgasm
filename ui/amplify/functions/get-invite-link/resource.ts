import { defineFunction } from '@aws-amplify/backend'

export const getInviteLinkFn = defineFunction({
  name: 'get-invite-link',
  entry: './handler.ts',
})
