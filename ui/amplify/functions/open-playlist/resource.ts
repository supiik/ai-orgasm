import { defineFunction } from '@aws-amplify/backend'

export const openPlaylistFn = defineFunction({
  name: 'open-playlist',
  entry: './handler.ts',
})
