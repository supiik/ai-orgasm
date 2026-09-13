import { defineFunction } from '@aws-amplify/backend'

export const listPlaylistsFn = defineFunction({
  name: 'list-playlists',
  entry: './handler.ts',
})
