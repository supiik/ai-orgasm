import { healthHandlers } from './health'
import { playlistHandlers } from './playlists'

export const handlers = [...healthHandlers, ...playlistHandlers]
