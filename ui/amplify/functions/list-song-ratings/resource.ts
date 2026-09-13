import { defineFunction } from '@aws-amplify/backend'

export const listSongRatingsFn = defineFunction({
  name: 'list-song-ratings',
  entry: './handler.ts',
})
