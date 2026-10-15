import { defineFunction } from '@aws-amplify/backend'

export const deleteInviteLinkFn = defineFunction({
  name: 'delete-invite-link',
  entry: './handler.ts',
})
