import { withAuth, queryParam } from '../../lib/http'
import { getAuthorizeUrl } from '../../lib/services/spotify'

/** GET ?playlistId=<id> — returns the Spotify authorize URL to redirect the browser to. */
export const handler = withAuth('authenticated-with-contributor', 200, async (event) => {
  return getAuthorizeUrl(queryParam(event, 'playlistId'))
})
