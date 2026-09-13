import { defineFunction } from '@aws-amplify/backend'

export const adminListOrgContributorsFn = defineFunction({
  name: 'admin-list-org-contributors',
  entry: './handler.ts',
})
