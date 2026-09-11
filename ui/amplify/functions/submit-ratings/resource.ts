import { defineFunction } from '@aws-amplify/backend'

export const submitRatingsFn = defineFunction({
  name: 'submit-ratings',
  entry: './handler.ts',
})
