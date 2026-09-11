import { withAuth } from '../../lib/http'

// Public health check. Deliberately touches no table and holds no IAM grant: it previously
// reported a playlist count, which meant every unauthenticated request read the whole tenant-1
// partition (queryAllPages) plus an N+1 lookup per row, and leaked that count to anyone.
export const handler = withAuth('public', 200, async () => ({ message: 'Hello from Lambda!' }))
