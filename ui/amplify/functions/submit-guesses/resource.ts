import { defineFunction } from '@aws-amplify/backend'

export const submitGuessesFn = defineFunction({
  name: 'submit-guesses',
  entry: './handler.ts',
})
