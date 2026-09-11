import { defineFunction } from '@aws-amplify/backend'

export const getSongFn = defineFunction({
  name: 'get-song',
  entry: './handler.ts',
})
