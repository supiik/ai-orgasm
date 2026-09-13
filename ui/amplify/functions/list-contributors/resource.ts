import { defineFunction } from '@aws-amplify/backend'

export const listContributorsFn = defineFunction({
  name: 'list-contributors',
  entry: './handler.ts',
})
