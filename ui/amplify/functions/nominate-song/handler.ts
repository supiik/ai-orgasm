import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { nominateSong, type NominateSongRequest } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 201, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  const request = await parseBody<NominateSongRequest>(event)
  requireFields({ songId: request.songId })
  return nominateSong(ctx.tenantId!, playlistId, ctx.contributorId!, request)
})
