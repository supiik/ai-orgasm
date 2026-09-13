import { defineFunction } from '@aws-amplify/backend'

export const adminAddOrgContributorFn = defineFunction({
  name: 'admin-add-org-contributor',
  entry: './handler.ts',
})
