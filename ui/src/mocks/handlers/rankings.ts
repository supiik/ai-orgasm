import { http, HttpResponse } from 'msw'
import { playlistsDb, nominationsDb, guessesDb } from './db'
import { db as contributorDb } from './contributors'

export interface RankingRow {
  playlistId: string
  playlistName: string
  contributorId: string
  contributorName: string
  contributorAvatarUrl: string | null
  rankPosition: number
  correctGuesses: number
  totalGuesses: number
}

function computeRankings(): RankingRow[] {
  const published = playlistsDb.filter(p => p.status === 'PUBLISHED')
  const rows: RankingRow[] = []

  for (const playlist of published) {
    const playlistNominations = nominationsDb.filter(
      n => n.playlistId === playlist.id && n.status === 'APPROVED',
    )
    const playlistGuesses = guessesDb.filter(g => g.playlistId === playlist.id)

    const nominatorMap = new Map(playlistNominations.map(n => [n.id, n.nominatedById]))

    const guesserIds = [...new Set(playlistGuesses.map(g => g.guesserId))]
    const stats = guesserIds.map(guesserId => {
      const guesses = playlistGuesses.filter(g => g.guesserId === guesserId)
      const correct = guesses.filter(g => {
        const nominatorId = nominatorMap.get(g.nominationId)
        return nominatorId != null && nominatorId === g.guessedContributorId
      }).length
      return { guesserId, correct, total: guesses.length }
    })
    stats.sort((a, b) => b.correct - a.correct)

    let rank = 1
    for (let i = 0; i < stats.length; i++) {
      if (i > 0 && stats[i].correct < stats[i - 1].correct) rank = i + 1
      const contributor = contributorDb.find(c => c.id === stats[i].guesserId)
      rows.push({
        playlistId: playlist.id,
        playlistName: playlist.name,
        contributorId: stats[i].guesserId,
        contributorName: contributor?.name ?? 'Unknown',
        contributorAvatarUrl: contributor?.avatarUrl ?? null,
        rankPosition: rank,
        correctGuesses: stats[i].correct,
        totalGuesses: stats[i].total,
      })
    }
  }

  return rows
}

export const rankingHandlers = [
  http.get('/api/v1/rankings', () => HttpResponse.json(computeRankings())),
]
