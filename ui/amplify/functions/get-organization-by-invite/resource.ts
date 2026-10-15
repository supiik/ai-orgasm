import { defineFunction } from '@aws-amplify/backend'

export const getOrganizationByInviteFn = defineFunction({
  name: 'get-organization-by-invite',
  entry: './handler.ts',
})
