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
