import { defineFunction } from '@aws-amplify/backend'

export const listOrganizationsFn = defineFunction({
  name: 'list-organizations',
  entry: './handler.ts',
})
