import { defineFunction } from '@aws-amplify/backend'

export const getCurrentContributorFn = defineFunction({
  name: 'get-current-contributor',
  entry: './handler.ts',
})
