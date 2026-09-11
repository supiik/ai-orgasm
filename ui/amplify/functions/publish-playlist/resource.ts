import { defineFunction } from '@aws-amplify/backend'

export const publishPlaylistFn = defineFunction({
  name: 'publish-playlist',
  entry: './handler.ts',
})
