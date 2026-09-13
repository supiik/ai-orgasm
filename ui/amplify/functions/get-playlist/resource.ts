import { defineFunction } from '@aws-amplify/backend'

export const getPlaylistFn = defineFunction({
  name: 'get-playlist',
  entry: './handler.ts',
})
