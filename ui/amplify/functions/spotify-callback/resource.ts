import { defineFunction } from '@aws-amplify/backend'

export const spotifyCallbackFn = defineFunction({
  name: 'spotify-callback',
  entry: './handler.ts',
})
