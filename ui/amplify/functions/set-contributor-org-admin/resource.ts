import { defineFunction } from '@aws-amplify/backend'

export const setContributorOrgAdminFn = defineFunction({
  name: 'set-contributor-org-admin',
  entry: './handler.ts',
})
