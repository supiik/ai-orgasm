import { defineFunction } from '@aws-amplify/backend'

export const createContributorFn = defineFunction({
  name: 'create-contributor',
  entry: './handler.ts',
})
