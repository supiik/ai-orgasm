import { defineFunction } from '@aws-amplify/backend'

export const listNominationsFn = defineFunction({
  name: 'list-nominations',
  entry: './handler.ts',
})
