import { defineFunction } from '@aws-amplify/backend'

export const listPlaylistsByContributorFn = defineFunction({
  name: 'list-playlists-by-contributor',
  entry: './handler.ts',
})
