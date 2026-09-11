import { defineFunction } from '@aws-amplify/backend'

export const deletePlaylistFn = defineFunction({
  name: 'delete-playlist',
  entry: './handler.ts',
})
