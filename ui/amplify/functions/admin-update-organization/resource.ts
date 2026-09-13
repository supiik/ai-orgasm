import { defineFunction } from '@aws-amplify/backend'

export const adminUpdateOrganizationFn = defineFunction({
  name: 'admin-update-organization',
  entry: './handler.ts',
})
