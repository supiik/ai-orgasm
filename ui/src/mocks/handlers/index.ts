import { healthHandlers } from './health'
import { contributorHandlers } from './contributors'
import { playlistHandlers } from './playlists'
import { nominationHandlers } from './nominations'
import { songHandlers } from './songs'
import { rankingHandlers } from './rankings'
import { songRatingHandlers } from './songRatings'

export const handlers = [...healthHandlers, ...contributorHandlers, ...playlistHandlers, ...nominationHandlers, ...songHandlers, ...rankingHandlers, ...songRatingHandlers]
