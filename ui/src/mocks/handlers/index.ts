import { healthHandlers } from './health'
import { contributorHandlers } from './contributors'
import { playlistHandlers } from './playlists'
import { songHandlers } from './songs'

export const handlers = [...healthHandlers, ...contributorHandlers, ...playlistHandlers, ...songHandlers]
