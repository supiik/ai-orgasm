import { defineFunction } from '@aws-amplify/backend'

export const updatePlaylistFn = defineFunction({
  name: 'update-playlist',
  entry: './handler.ts',
})
