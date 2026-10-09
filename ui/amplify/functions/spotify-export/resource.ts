import { defineFunction } from '@aws-amplify/backend'

export const spotifyExportFn = defineFunction({
  name: 'spotify-export',
  entry: './handler.ts',
  // Loops one Spotify search call per approved nomination, then create/update-playlist calls —
  // can take longer than the 3 s default for a playlist with many songs.
  timeoutSeconds: 30,
})
