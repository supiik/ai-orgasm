import { http, HttpResponse } from 'msw'
import { playlistsDb, nominationsDb } from './db'

// Mock of the Spotify playlist-export endpoints (SpotifyController.java / spotify-* Lambda
// functions) at the REST paths api-backend.ts's spotify client uses — see CLAUDE.md "Spotify
// playlist export". Real Spotify OAuth is never reached in dev/e2e: `authorizeUrl` points
// straight back at the app's own /spotify/callback route with a fake code/state, so
// connectSpotify()'s window.location.href navigation exercises the full round trip without
// leaving the app. `connected` is one module-level flag (mock mode has a single active session,
// unlike the real per-contributor connection).

let connected = false

const error = (status: number, message: string) => HttpResponse.json({ error: message }, { status })

export const spotifyHandlers = [
  http.get('/api/v1/spotify/status', () => HttpResponse.json({ connected })),

  http.get('/api/v1/spotify/connect', ({ request }) => {
    const playlistId = new URL(request.url).searchParams.get('playlistId') ?? ''
    const state = encodeURIComponent(playlistId)
    const authorizeUrl = `${new URL(request.url).origin}/spotify/callback?code=mock-code&state=${state}`
    return HttpResponse.json({ authorizeUrl })
  }),

  http.post('/api/v1/spotify/callback', async ({ request }) => {
    const body = await request.json() as { code?: string; state?: string }
    if (!body.code || body.state === undefined) return error(400, 'code and state must not be blank')
    connected = true
    return HttpResponse.json({ playlistId: body.state || undefined })
  }),

  http.post('/api/v1/spotify/playlists/:id/export', ({ params }) => {
    if (!connected) return error(409, 'Spotify is not connected')
    const playlist = playlistsDb.find(p => p.id === params.id)
    if (!playlist) return error(404, `Playlist not found: ${params.id}`)
    if (playlist.status === 'NEW' || playlist.status === 'OPEN') {
      return error(409, `Nominations are not final yet, playlist status is ${playlist.status}`)
    }
    const approved = nominationsDb.filter(n => n.playlistId === playlist.id && n.status === 'APPROVED')
    playlist.spotifyPlaylistId ??= `mock-${playlist.id}`
    playlist.spotifyPlaylistUrl = `https://open.spotify.com/playlist/${playlist.spotifyPlaylistId}`
    return HttpResponse.json({
      spotifyPlaylistId: playlist.spotifyPlaylistId,
      spotifyPlaylistUrl: playlist.spotifyPlaylistUrl,
      matchedCount: approved.length,
      skippedSongs: [] as string[],
    })
  }),
]

/** Test seam — Playwright specs that need a clean "not connected" state can reach for this if a
 * future spec runs multiple Spotify scenarios in one page session. */
export function resetMockSpotifyConnection(): void {
  connected = false
}
