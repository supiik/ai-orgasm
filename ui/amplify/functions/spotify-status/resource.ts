import { defineFunction } from '@aws-amplify/backend'

export const spotifyStatusFn = defineFunction({
  name: 'spotify-status',
  entry: './handler.ts',
})
