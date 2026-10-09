import { defineFunction } from '@aws-amplify/backend'

export const spotifyConnectFn = defineFunction({
  name: 'spotify-connect',
  entry: './handler.ts',
})
