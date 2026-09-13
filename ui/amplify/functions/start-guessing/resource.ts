import { defineFunction } from '@aws-amplify/backend'

export const startGuessingFn = defineFunction({
  name: 'start-guessing',
  entry: './handler.ts',
})
