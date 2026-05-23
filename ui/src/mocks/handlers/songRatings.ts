import { http, HttpResponse } from 'msw'
import { playlistsDb } from './db'
import { db as contributorDb } from './contributors'

interface SongRatingRow {
  playlistId: string
  contributorId: string
  nominationId: string
  points: number
}

export const songRatingsDb: SongRatingRow[] = []

const VALID_POINTS: Record<string, number[]> = {
  LINEAR: [1, 2, 3],
  FIBONACCI: [5, 8, 13],
  BEST_SONG: [1],
}

export const songRatingHandlers = [
  http.get('/api/v1/playlists/:id/ratings', ({ params }) => {
    const playlistId = params.id as string
    const rows = songRatingsDb.filter(r => r.playlistId === playlistId)
    return HttpResponse.json(
      rows.map(r => {
        const contributor = contributorDb.find(c => c.id === r.contributorId)
        return {
          nominationId: r.nominationId,
          contributorId: r.contributorId,
          contributorName: contributor?.name ?? 'Unknown',
          points: r.points,
        }
      }),
    )
  }),

  http.post('/api/v1/playlists/:id/ratings', async ({ params, request }) => {
    const playlistId = params.id as string
    const playlist = playlistsDb.find(p => p.id === playlistId)
    if (!playlist) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    if (playlist.status !== 'PUBLISHED') {
      return HttpResponse.json({ title: 'Conflict', detail: 'Ratings can only be submitted for published playlists' }, { status: 409 })
    }
    if (!playlist.ratingType) {
      return HttpResponse.json({ title: 'Conflict', detail: 'No rating type configured' }, { status: 409 })
    }
    const body = await request.json() as { contributorId: string; ratings: Array<{ nominationId: string; points: number }> }
    const expected = VALID_POINTS[playlist.ratingType]
    const actual = body.ratings.map(r => r.points).sort((a, b) => a - b)
    if (JSON.stringify(actual) !== JSON.stringify(expected)) {
      return HttpResponse.json({ title: 'Conflict', detail: `Invalid rating points for ${playlist.ratingType}` }, { status: 409 })
    }
    for (let i = songRatingsDb.length - 1; i >= 0; i--) {
      if (songRatingsDb[i].playlistId === playlistId && songRatingsDb[i].contributorId === body.contributorId) {
        songRatingsDb.splice(i, 1)
      }
    }
    for (const item of body.ratings) {
      songRatingsDb.push({ playlistId, contributorId: body.contributorId, nominationId: item.nominationId, points: item.points })
    }
    return new HttpResponse(null, { status: 204 })
  }),
]
