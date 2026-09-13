// One class per BaseHandler.java catch-chain entry — lib/http.ts's withAuth() maps these to
// the same status codes the Java handlers use.

export class ValidationError extends Error {
  constructor(public readonly errors: string[]) {
    super(errors.join('; '))
    this.name = 'ValidationError'
  }
}

export class UnauthorizedError extends Error {
  constructor(message = 'Missing or invalid bearer token') {
    super(message)
    this.name = 'UnauthorizedError'
  }
}

export class NotLinkedError extends Error {
  constructor(message = 'No contributor linked to this account') {
    super(message)
    this.name = 'NotLinkedError'
  }
}

/** Valid token, but the caller lacks the role the endpoint requires (e.g. the `admins` group). */
export class ForbiddenError extends Error {
  constructor(message = 'Insufficient permissions') {
    super(message)
    this.name = 'ForbiddenError'
  }
}

export class NotFoundError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'NotFoundError'
  }
}

export class ConflictError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ConflictError'
  }
}

/** An external service this request depends on (e.g. the song-search catalogue) failed or timed out. */
export class UpstreamError extends Error {
  constructor(message = 'Upstream service unavailable') {
    super(message)
    this.name = 'UpstreamError'
  }
}
