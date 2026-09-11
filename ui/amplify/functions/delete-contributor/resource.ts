import { defineFunction } from '@aws-amplify/backend'

export const deleteContributorFn = defineFunction({
  name: 'delete-contributor',
  entry: './handler.ts',
})
