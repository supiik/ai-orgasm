import { describe, expect, it, vi } from 'vitest'
import type { APIGatewayProxyEventV2 } from 'aws-lambda'
import { ValidationError } from './errors'

// http.ts imports auth.ts, which builds the Cognito verifier at module load and needs a real
// user pool id — irrelevant to the pure helpers under test here.
vi.mock('./auth', () => ({ verifyRequest: vi.fn() }))
vi.mock('./repositories/contributor', () => ({ findContributorByCognitoSub: vi.fn() }))

import { MAX_PAGE_SIZE, pageable } from './http'

const event = (query: Record<string, string>): APIGatewayProxyEventV2 =>
  ({ queryStringParameters: query, rawPath: '/' }) as unknown as APIGatewayProxyEventV2

describe('pageable', () => {
  it('is unpaged when size is absent', () => {
    expect(pageable(event({}))).toBeUndefined()
  })

  it('parses page and size', () => {
    expect(pageable(event({ page: '2', size: '20' }))).toEqual({ page: 2, size: 20 })
  })

  it('defaults page to 0', () => {
    expect(pageable(event({ size: '20' }))).toEqual({ page: 0, size: 20 })
  })

  it('caps size at MAX_PAGE_SIZE', () => {
    expect(pageable(event({ size: '999999' }))?.size).toBe(MAX_PAGE_SIZE)
  })

  // Number('abc') is NaN, which previously slid through and silently produced an empty page.
  it.each<Record<string, string>>([
    { size: 'abc' },
    { size: '0' },
    { size: '-5' },
    { size: '1.5' },
    { size: '10', page: 'abc' },
    { size: '10', page: '-1' },
  ])('rejects %j as a validation error', (query) => {
    expect(() => pageable(event(query))).toThrow(ValidationError)
  })
})
