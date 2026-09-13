import { defineFunction } from '@aws-amplify/backend'

export const createSongFn = defineFunction({
  name: 'create-song',
  entry: './handler.ts',
})
