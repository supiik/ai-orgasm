import { http, HttpResponse } from 'msw'

// Deterministic stand-in for the real iTunes Search API that HomeView queries for cover
// artwork, keyed by a lowercase match on the "<artist> <title>" term it sends. Only "Creep" is
// given a distinct match below; every other query — including two different seeded songs —
// deliberately falls through to the same generic cover, mirroring how the real iTunes search
// can legitimately return the same (often wrong) result for different queries. This makes
// HomeView's duplicate-artwork guard (see HomeView.vue) exercisable in mock mode/e2e without
// depending on the real, non-deterministic network API.
const ARTWORK: Record<string, string> = {
  'radiohead creep': 'https://mock-artwork.test/creep-100x100bb.jpg',
}
const FALLBACK_ARTWORK = 'https://mock-artwork.test/generic-100x100bb.jpg'

export const artworkHandlers = [
  http.get('https://itunes.apple.com/search', ({ request }) => {
    const term = new URL(request.url).searchParams.get('term')?.toLowerCase() ?? ''
    return HttpResponse.json({ results: [{ artworkUrl100: ARTWORK[term] ?? FALLBACK_ARTWORK }] })
  }),
]
