import { healthHandlers } from './health'
import { sampleHandlers } from './samples'

export const handlers = [...healthHandlers, ...sampleHandlers]
