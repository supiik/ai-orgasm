import { defineFunction } from '@aws-amplify/backend'

export const updateContributorFn = defineFunction({
  name: 'update-contributor',
  entry: './handler.ts',
})
