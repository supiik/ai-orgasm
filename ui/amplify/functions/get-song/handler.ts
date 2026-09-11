import { withAuth, pathSegment } from '../../lib/http'
import { getSongById } from '../../lib/services/song'
import { NotFoundError } from '../../lib/errors'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const id = pathSegment(event, 0)
  const song = await getSongById(ctx.tenantId!, id)
  if (!song) throw new NotFoundError(`Song not found: ${id}`)
  return song
})
