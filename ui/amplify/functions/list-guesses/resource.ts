import { defineFunction } from '@aws-amplify/backend'

export const listGuessesFn = defineFunction({
  name: 'list-guesses',
  entry: './handler.ts',
})
