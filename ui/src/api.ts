import { authMode } from '@/authMode'
import { api as backendApi } from './api-backend'
import { api as lambdaApi } from './api-lambda'

export const api = authMode === 'cognito' ? lambdaApi : backendApi

export type { GuessEntry, RankingEntry, SongRatingEntry, SongNomination } from './api-backend'
