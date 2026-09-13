import { defineFunction } from '@aws-amplify/backend'

export const listSongsFn = defineFunction({
  name: 'list-songs',
  entry: './handler.ts',
})
