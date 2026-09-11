import { defineFunction } from '@aws-amplify/backend'

export const helloFn = defineFunction({
  name: 'hello',
  entry: './handler.ts',
})
