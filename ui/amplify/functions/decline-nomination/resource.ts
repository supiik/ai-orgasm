import { defineFunction } from '@aws-amplify/backend'

export const declineNominationFn = defineFunction({
  name: 'decline-nomination',
  entry: './handler.ts',
})
