import { defineFunction } from '@aws-amplify/backend'

export const adminListOrganizationsFn = defineFunction({
  name: 'admin-list-organizations',
  entry: './handler.ts',
})
