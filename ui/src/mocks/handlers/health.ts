import { http, HttpResponse } from 'msw'

export const healthHandlers = [
  http.get('/api/health', () => {
    return HttpResponse.json({
      success: true,
      data: { status: 'UP' },
      message: 'Mock backend healthy',
      timestamp: new Date().toISOString(),
    })
  }),
]
