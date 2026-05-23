import { http, HttpResponse } from 'msw'
import { db as contributorDb } from './contributors'
import { playlistsDb, nominationsDb, guessesDb } from './db'

const nextId = () => `play-${Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join('')}`

export { playlistsDb as db }

const now = () => new Date().toISOString()

export const playlistHandlers = [
  http.get('/api/v1/playlists', ({ request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const name = url.searchParams.get('name')?.toLowerCase()
    const filtered = name ? playlistsDb.filter(p => p.name.toLowerCase().includes(name)) : playlistsDb
    const content = filtered.slice(page * size, page * size + size)
    return HttpResponse.json({
      content,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      number: page,
      size,
    })
  }),

  http.post('/api/v1/playlists', async ({ request }) => {
    const body = await request.json() as { name: string; description?: string }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    const created = {
      id: nextId(),
      name: body.name,
      description: body.description ?? null,
      status: 'NEW' as const,
      leadContributorId: null,
      leadContributorName: null,
      leadContributorAvatarUrl: null,
      deadline: null,
      version: 0,
      createdAt: now(),
      updatedAt: now(),
    }
    playlistsDb.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.get('/api/v1/playlists/:id', ({ params }) => {
    const playlist = playlistsDb.find(p => p.id === params.id)
    if (!playlist) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    return HttpResponse.json(playlist)
  }),

  http.put('/api/v1/playlists/:id', async ({ params, request }) => {
    const index = playlistsDb.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    const body = await request.json() as { name: string; description?: string }
    if (!body.name?.trim()) {
      return HttpResponse.json({ message: 'Name is required' }, { status: 400 })
    }
    playlistsDb[index] = {
      ...playlistsDb[index],
      name: body.name,
      description: body.description ?? null,
      version: playlistsDb[index].version + 1,
      updatedAt: now(),
    }
    return HttpResponse.json(playlistsDb[index])
  }),

  http.delete('/api/v1/playlists/:id', ({ params }) => {
    const index = playlistsDb.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    playlistsDb.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/api/v1/playlists/:id/open', async ({ params, request }) => {
    const index = playlistsDb.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    if (playlistsDb[index].status !== 'NEW') {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only NEW playlists can be opened' }, { status: 409 })
    }
    const body = await request.json() as { contributorId: string; deadline: string }
    const contributor = contributorDb.find(c => c.id === body.contributorId)
    playlistsDb[index] = { ...playlistsDb[index], status: 'OPEN', leadContributorId: body.contributorId, leadContributorName: contributor?.name ?? null, leadContributorAvatarUrl: contributor?.avatarUrl ?? null, deadline: body.deadline, updatedAt: now() }
    return HttpResponse.json(playlistsDb[index])
  }),

  http.post('/api/v1/playlists/:id/start-guessing', async ({ params, request }) => {
    const index = playlistsDb.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    if (playlistsDb[index].status !== 'OPEN') {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only open playlists can start guessing' }, { status: 409 })
    }
    const body = await request.json() as { contributorId: string }
    if (playlistsDb[index].leadContributorId !== body.contributorId) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only the lead contributor can start guessing' }, { status: 409 })
    }
    const deadlinePassed = playlistsDb[index].deadline ? new Date(playlistsDb[index].deadline!) < new Date() : true
    const pending = nominationsDb.filter(n => n.playlistId === params.id && n.status === 'PENDING')
    if (!deadlinePassed && pending.length > 0) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Deadline has not passed and there are still pending nominations' }, { status: 409 })
    }
    pending.forEach(n => {
      const idx = nominationsDb.findIndex(nom => nom.id === n.id)
      if (idx !== -1) nominationsDb[idx] = { ...nominationsDb[idx], status: 'DECLINED', version: nominationsDb[idx].version + 1, updatedAt: now() }
    })
    const guessingDeadline = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString()
    playlistsDb[index] = { ...playlistsDb[index], status: 'GUESSING', guessingDeadline, updatedAt: now() }
    return HttpResponse.json(playlistsDb[index])
  }),

  http.post('/api/v1/playlists/:id/submit-guesses', async ({ params, request }) => {
    const index = playlistsDb.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    if (playlistsDb[index].status !== 'GUESSING') {
      return HttpResponse.json({ title: 'Conflict', detail: 'Playlist is not in guessing phase' }, { status: 409 })
    }
    const body = await request.json() as { contributorId: string; guesses: Array<{ nominationId: string; guessedContributorId: string }> }
    const playlistId = params.id as string
    for (let i = guessesDb.length - 1; i >= 0; i--) {
      if (guessesDb[i].playlistId === playlistId && guessesDb[i].guesserId === body.contributorId) {
        guessesDb.splice(i, 1)
      }
    }
    for (const item of body.guesses) {
      guessesDb.push({ playlistId, nominationId: item.nominationId, guesserId: body.contributorId, guessedContributorId: item.guessedContributorId })
    }
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/v1/playlists/:id/guesses', ({ params }) => {
    const playlistId = params.id as string
    return HttpResponse.json(guessesDb.filter(g => g.playlistId === playlistId))
  }),

  http.post('/api/v1/playlists/:id/publish', async ({ params, request }) => {
    const index = playlistsDb.findIndex(p => p.id === params.id)
    if (index === -1) return HttpResponse.json({ message: 'Not found' }, { status: 404 })
    if (playlistsDb[index].status !== 'GUESSING') {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only playlists in the guessing phase can be published' }, { status: 409 })
    }
    const body = await request.json() as { contributorId: string }
    if (playlistsDb[index].leadContributorId !== body.contributorId) {
      return HttpResponse.json({ title: 'Conflict', detail: 'Only the lead contributor can publish' }, { status: 409 })
    }
    playlistsDb[index] = { ...playlistsDb[index], status: 'PUBLISHED', updatedAt: now() }
    return HttpResponse.json(playlistsDb[index])
  }),

  http.get('/api/v1/contributors/:id/playlists', ({ params, request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const filtered = playlistsDb.filter(p => p.leadContributorId === params.id)
    const content = filtered.slice(page * size, page * size + size)
    return HttpResponse.json({
      content,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      number: page,
      size,
    })
  }),
]
