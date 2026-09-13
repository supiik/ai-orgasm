import { defineFunction } from '@aws-amplify/backend'

export const deleteSongFn = defineFunction({
  name: 'delete-song',
  entry: './handler.ts',
})
