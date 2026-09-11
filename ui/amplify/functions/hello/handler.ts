import { withAuth } from '../../lib/http'
import { listPlaylists } from '../../lib/services/playlist'

// Public health check — DynamoTenantContext.get() defaults to tenant 1 in the Java source when
// unset (no auth to derive it from here either); mirrored explicitly since 'public' mode never
// resolves a tenant.
export const handler = withAuth('public', 200, async () => {
  const { totalElements } = await listPlaylists(1, {}, { page: 0, size: 10 })
  return { message: 'Hello from Lambda!', totalPlaylists: totalElements }
})
