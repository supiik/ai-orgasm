import { defineFunction } from '@aws-amplify/backend'

export const listContributedPlaylistsByContributorFn = defineFunction({
  name: 'list-contributed-playlists-by-contributor',
  entry: './handler.ts',
})
