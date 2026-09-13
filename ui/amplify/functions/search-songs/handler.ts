import { withAuth, queryParam } from '../../lib/http'
import { searchSongs } from '../../lib/services/songSearch'

/** GET ?q=<free text>&limit=<n> — pre-fills the song form from an external catalogue. */
export const handler = withAuth('authenticated-with-contributor', 200, async (event) => {
  const limitParam = queryParam(event, 'limit')
  const limit = limitParam === undefined ? undefined : Number(limitParam)
  return searchSongs(queryParam(event, 'q'), limit)
})
