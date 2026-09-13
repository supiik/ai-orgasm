import { defineFunction } from '@aws-amplify/backend'

export const nominateSongFn = defineFunction({
  name: 'nominate-song',
  entry: './handler.ts',
})
