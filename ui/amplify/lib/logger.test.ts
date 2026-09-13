import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  EMAIL_PLACEHOLDER,
  Fields,
  addLogContext,
  getLogContext,
  log,
  maskPii,
  runWithLogContext,
  setLogSink,
} from './logger'

describe('logger', () => {
  let lines: Record<string, any>[]

  beforeEach(() => {
    lines = []
    setLogSink((line) => lines.push(JSON.parse(line)))
    process.env.AWS_LAMBDA_FUNCTION_NAME = 'get-playlist'
    process.env.APP_ENV = 'test'
    delete process.env.LOG_LEVEL
  })

  afterEach(() => {
    setLogSink(undefined)
    delete process.env.AWS_LAMBDA_FUNCTION_NAME
    delete process.env.APP_ENV
    delete process.env.LOG_LEVEL
  })

  it('emits one ECS-shaped JSON object per line', () => {
    log.info('hello', { [Fields.RESPONSE_STATUS]: 200, [Fields.EVENT_DURATION]: 1234n })

    expect(lines).toHaveLength(1)
    const line = lines[0]
    expect(line['@timestamp']).toMatch(/^\d{4}-\d{2}-\d{2}T/)
    expect(line.log).toEqual({ level: 'INFO', logger: 'orgasm-lambda' })
    expect(line.message).toBe('hello')
    expect(line.service).toMatchObject({ name: 'get-playlist', environment: 'test' })
    expect(line.process.pid).toBe(process.pid)
    expect(line.ecs).toEqual({ version: '8.11' })
    // dotted field names nest; bigints serialise as strings
    expect(line.http).toEqual({ response: { status_code: 200 } })
    expect(line.event).toEqual({ duration: '1234' })
  })

  it('inherits the active context and lets it grow after the fact', () => {
    runWithLogContext({ [Fields.TRACE_ID]: 'abc', [Fields.REQUEST_ID]: 'r1' }, () => {
      addLogContext({ [Fields.TENANT_ID]: 7 })
      log.info('inside')
      runWithLogContext({ [Fields.SPAN_ID]: 's2' }, () => log.info('nested'))
    })
    log.info('outside')

    expect(lines[0]).toMatchObject({ trace: { id: 'abc' }, http: { request: { id: 'r1' } }, tenant: { id: 7 } })
    expect(lines[1]).toMatchObject({ trace: { id: 'abc' }, span: { id: 's2' }, tenant: { id: 7 } })
    expect(lines[2].trace).toBeUndefined()
    expect(lines[2].tenant).toBeUndefined()
    expect(getLogContext()).toEqual({})
  })

  it('survives awaits inside the context', async () => {
    await runWithLogContext({ [Fields.REQUEST_ID]: 'r1' }, async () => {
      await new Promise((resolve) => setTimeout(resolve, 1))
      log.info('after await')
    })
    expect(lines[0].http.request.id).toBe('r1')
  })

  it('attaches error type, message and stack', () => {
    log.error('boom', new RangeError('out of range'))

    expect(lines[0].log.level).toBe('ERROR')
    expect(lines[0].error).toMatchObject({ type: 'RangeError', message: 'out of range' })
    expect(lines[0].error.stack_trace).toContain('RangeError: out of range')
  })

  it('masks email addresses in messages, fields, context and errors', () => {
    runWithLogContext({ 'user.email': 'ctx@example.com' }, () => {
      log.error('Failed for alice@example.com', new Error('duplicate bob@example.com'), { note: 'cc carol@example.com' })
    })

    const raw = JSON.stringify(lines[0])
    expect(raw).not.toContain('example.com')
    expect(lines[0].message).toBe(`Failed for ${EMAIL_PLACEHOLDER}`)
    expect(lines[0].user.email).toBe(EMAIL_PLACEHOLDER)
    expect(lines[0].note).toBe(`cc ${EMAIL_PLACEHOLDER}`)
    expect(lines[0].error.message).toBe(`duplicate ${EMAIL_PLACEHOLDER}`)
    expect(lines[0].error.stack_trace).not.toContain('bob@')
  })

  it('honours LOG_LEVEL', () => {
    log.debug('hidden by default')
    expect(lines).toHaveLength(0)

    process.env.LOG_LEVEL = 'debug'
    log.debug('now visible')
    process.env.LOG_LEVEL = 'ERROR'
    log.warn('suppressed')
    log.error('kept')

    expect(lines.map((l) => l.message)).toEqual(['now visible', 'kept'])
  })

  it('drops undefined/null fields rather than emitting them', () => {
    log.info('sparse', { [Fields.TENANT_ID]: undefined, [Fields.USER_ID]: null, kept: 1 })
    expect(lines[0]).not.toHaveProperty('tenant')
    expect(lines[0]).not.toHaveProperty('user')
    expect(lines[0].kept).toBe(1)
  })
})

describe('maskPii', () => {
  it.each(['alice@example.com', 'first.last+tag@sub.example.co.uk', "o'neil_99@example-mail.org"])(
    'masks %s',
    (email) => {
      expect(maskPii(`to ${email} now`)).toBe(`to ${EMAIL_PLACEHOLDER} now`)
    },
  )

  it('leaves non-emails alone', () => {
    expect(maskPii('Playlist 42 by contributor 7')).toBe('Playlist 42 by contributor 7')
    expect(maskPii('@mention')).toBe('@mention')
  })
})
