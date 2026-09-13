import { defineFunction } from '@aws-amplify/backend'

export const approveNominationFn = defineFunction({
  name: 'approve-nomination',
  entry: './handler.ts',
})
