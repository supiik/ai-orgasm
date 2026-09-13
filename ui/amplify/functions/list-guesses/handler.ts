import { withAuth, pathSegment } from '../../lib/http'
import { getGuesses } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event) => {
  const playlistId = pathSegment(event, 1)
  return getGuesses(playlistId)
})
