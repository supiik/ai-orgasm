import { defineFunction } from '@aws-amplify/backend'

export const linkContributorFn = defineFunction({
  name: 'link-contributor',
  entry: './handler.ts',
})
