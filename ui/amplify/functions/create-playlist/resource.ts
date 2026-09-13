import { defineFunction } from '@aws-amplify/backend'

export const createPlaylistFn = defineFunction({
  name: 'create-playlist',
  entry: './handler.ts',
})
