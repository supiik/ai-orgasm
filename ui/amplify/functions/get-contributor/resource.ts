import { defineFunction } from '@aws-amplify/backend'

export const getContributorFn = defineFunction({
  name: 'get-contributor',
  entry: './handler.ts',
})
