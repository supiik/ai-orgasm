<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api, type RankingEntry } from '@/api'
import { Trophy, Target, TrendingUp, Users } from 'lucide-vue-next'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const rankings = ref<RankingEntry[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

async function fetchRankings() {
  loading.value = true
  error.value = null
  try {
    const { data } = await api.rankings().list()
    rankings.value = data
  } catch {
    error.value = 'Failed to load rankings.'
  } finally {
    loading.value = false
  }
}

onMounted(fetchRankings)

interface LeaderboardEntry {
  contributorId: string
  contributorName: string
  contributorAvatarUrl: string | null
  totalCorrect: number
  totalGuesses: number
  playlistCount: number
  accuracy: number
  avgRank: number
}

const leaderboard = computed<LeaderboardEntry[]>(() => {
  const map = new Map<string, LeaderboardEntry>()
  for (const r of rankings.value) {
    const existing = map.get(r.contributorId)
    if (existing) {
      existing.totalCorrect += r.correctGuesses
      existing.totalGuesses += r.totalGuesses
      existing.playlistCount++
      existing.avgRank += r.rankPosition
    } else {
      map.set(r.contributorId, {
        contributorId: r.contributorId,
        contributorName: r.contributorName,
        contributorAvatarUrl: r.contributorAvatarUrl,
        totalCorrect: r.correctGuesses,
        totalGuesses: r.totalGuesses,
        playlistCount: 1,
        accuracy: 0,
        avgRank: r.rankPosition,
      })
    }
  }
  const entries = [...map.values()]
  for (const e of entries) {
    e.accuracy = e.totalGuesses > 0 ? (e.totalCorrect / e.totalGuesses) * 100 : 0
    e.avgRank = e.avgRank / e.playlistCount
  }
  entries.sort((a, b) => b.totalCorrect - a.totalCorrect || a.avgRank - b.avgRank)
  return entries
})

const playlistGroups = computed(() => {
  const map = new Map<string, { playlistName: string; entries: RankingEntry[] }>()
  for (const r of rankings.value) {
    const group = map.get(r.playlistId)
    if (group) {
      group.entries.push(r)
    } else {
      map.set(r.playlistId, { playlistName: r.playlistName, entries: [r] })
    }
  }
  return [...map.values()]
})

const totalPlaylists = computed(() => playlistGroups.value.length)
const totalParticipants = computed(() => leaderboard.value.length)
const overallAccuracy = computed(() => {
  const total = rankings.value.reduce((s, r) => s + r.totalGuesses, 0)
  const correct = rankings.value.reduce((s, r) => s + r.correctGuesses, 0)
  return total > 0 ? ((correct / total) * 100).toFixed(0) : '—'
})
const topGuesser = computed(() => leaderboard.value.length > 0 ? leaderboard.value[0].contributorName : '—')

function formatAccuracy(correct: number, total: number) {
  return total > 0 ? `${((correct / total) * 100).toFixed(0)}%` : '—'
}

function rankBadge(rank: number) {
  if (rank === 1) return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
  if (rank === 2) return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'
  if (rank === 3) return 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200'
  return 'bg-muted text-muted-foreground'
}
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-semibold">Stats</h1>

    <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

    <!-- Summary cards -->
    <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
      <div class="rounded-lg border border-border p-4 space-y-1">
        <div class="flex items-center gap-2 text-sm text-muted-foreground">
          <Trophy class="h-4 w-4" />
          Top Guesser
        </div>
        <div class="text-lg font-semibold">{{ topGuesser }}</div>
      </div>
      <div class="rounded-lg border border-border p-4 space-y-1">
        <div class="flex items-center gap-2 text-sm text-muted-foreground">
          <Target class="h-4 w-4" />
          Overall Accuracy
        </div>
        <div class="text-lg font-semibold">{{ overallAccuracy }}{{ overallAccuracy !== '—' ? '%' : '' }}</div>
      </div>
      <div class="rounded-lg border border-border p-4 space-y-1">
        <div class="flex items-center gap-2 text-sm text-muted-foreground">
          <TrendingUp class="h-4 w-4" />
          Published Playlists
        </div>
        <div class="text-lg font-semibold">{{ totalPlaylists }}</div>
      </div>
      <div class="rounded-lg border border-border p-4 space-y-1">
        <div class="flex items-center gap-2 text-sm text-muted-foreground">
          <Users class="h-4 w-4" />
          Participants
        </div>
        <div class="text-lg font-semibold">{{ totalParticipants }}</div>
      </div>
    </div>

    <!-- Loading -->
    <template v-if="loading">
      <div class="space-y-3">
        <div v-for="i in 3" :key="i" class="h-10 rounded bg-muted animate-pulse" />
      </div>
    </template>

    <!-- All-time leaderboard -->
    <template v-else-if="leaderboard.length > 0">
      <div class="space-y-2">
        <h2 class="text-lg font-semibold">All-Time Leaderboard</h2>
        <div class="rounded-md border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead class="w-12">#</TableHead>
                <TableHead class="w-10" />
                <TableHead>Contributor</TableHead>
                <TableHead class="text-right">Correct</TableHead>
                <TableHead class="text-right">Total</TableHead>
                <TableHead class="text-right">Accuracy</TableHead>
                <TableHead class="text-right">Playlists</TableHead>
                <TableHead class="text-right">Avg Rank</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="(entry, i) in leaderboard" :key="entry.contributorId">
                <TableCell>
                  <span
                    class="inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium"
                    :class="rankBadge(i + 1)"
                  >{{ i + 1 }}</span>
                </TableCell>
                <TableCell>
                  <img v-if="entry.contributorAvatarUrl" :src="entry.contributorAvatarUrl" :alt="entry.contributorName" class="h-7 w-7 rounded-full object-cover" />
                  <div v-else class="h-7 w-7 rounded-full bg-muted flex items-center justify-center text-xs text-muted-foreground font-medium">
                    {{ entry.contributorName[0].toUpperCase() }}
                  </div>
                </TableCell>
                <TableCell class="font-medium">{{ entry.contributorName }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ entry.totalCorrect }}</TableCell>
                <TableCell class="text-right tabular-nums text-muted-foreground">{{ entry.totalGuesses }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ formatAccuracy(entry.totalCorrect, entry.totalGuesses) }}</TableCell>
                <TableCell class="text-right tabular-nums text-muted-foreground">{{ entry.playlistCount }}</TableCell>
                <TableCell class="text-right tabular-nums text-muted-foreground">{{ entry.avgRank.toFixed(1) }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>
      </div>

      <!-- Per-playlist rankings -->
      <div class="space-y-4">
        <h2 class="text-lg font-semibold">Rankings by Playlist</h2>
        <div v-for="group in playlistGroups" :key="group.playlistName" class="space-y-2">
          <h3 class="text-sm font-medium text-muted-foreground">{{ group.playlistName }}</h3>
          <div class="rounded-md border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead class="w-12">Rank</TableHead>
                  <TableHead class="w-10" />
                  <TableHead>Contributor</TableHead>
                  <TableHead class="text-right">Correct</TableHead>
                  <TableHead class="text-right">Total</TableHead>
                  <TableHead class="text-right">Accuracy</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="r in group.entries" :key="r.contributorId">
                  <TableCell>
                    <span
                      class="inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium"
                      :class="rankBadge(r.rankPosition)"
                    >{{ r.rankPosition }}</span>
                  </TableCell>
                  <TableCell>
                    <img v-if="r.contributorAvatarUrl" :src="r.contributorAvatarUrl" :alt="r.contributorName" class="h-7 w-7 rounded-full object-cover" />
                    <div v-else class="h-7 w-7 rounded-full bg-muted flex items-center justify-center text-xs text-muted-foreground font-medium">
                      {{ r.contributorName[0].toUpperCase() }}
                    </div>
                  </TableCell>
                  <TableCell class="font-medium">{{ r.contributorName }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{ r.correctGuesses }}</TableCell>
                  <TableCell class="text-right tabular-nums text-muted-foreground">{{ r.totalGuesses }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{ formatAccuracy(r.correctGuesses, r.totalGuesses) }}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </div>
      </div>
    </template>

    <div v-else-if="!loading" class="text-center text-muted-foreground py-10">
      No ranking data yet. Rankings appear after a playlist is published.
    </div>
  </div>
</template>
