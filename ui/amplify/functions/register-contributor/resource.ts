import { defineFunction } from '@aws-amplify/backend'

export const registerContributorFn = defineFunction({
  name: 'register-contributor',
  entry: './handler.ts',
})
