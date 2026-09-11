import { defineFunction } from '@aws-amplify/backend'

export const listRankingsFn = defineFunction({
  name: 'list-rankings',
  entry: './handler.ts',
})
