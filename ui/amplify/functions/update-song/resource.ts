import { defineFunction } from '@aws-amplify/backend'

export const updateSongFn = defineFunction({
  name: 'update-song',
  entry: './handler.ts',
})
