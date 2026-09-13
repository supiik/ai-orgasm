import { defineFunction } from '@aws-amplify/backend'

export const adminCreateOrganizationFn = defineFunction({
  name: 'admin-create-organization',
  entry: './handler.ts',
})
