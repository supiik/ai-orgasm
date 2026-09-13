import { defineFunction } from '@aws-amplify/backend'

export const searchSongsFn = defineFunction({
  name: 'search-songs',
  entry: './handler.ts',
  // Calls out to an external catalogue (MusicBrainz) that can take a couple of seconds; the
  // 3 s default would cut it off before the provider's own 8 s fetch timeout has a chance.
  timeoutSeconds: 15,
})
