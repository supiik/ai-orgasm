import { defineFunction } from '@aws-amplify/backend'

export const listSongNominationsFn = defineFunction({
  name: 'list-song-nominations',
  entry: './handler.ts',
})
