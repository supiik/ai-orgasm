import { healthHandlers } from './health'
import { playlistHandlers } from './playlists'
import { songHandlers } from './songs'

export const handlers = [...healthHandlers, ...playlistHandlers, ...songHandlers]
