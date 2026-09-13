import { CognitoJwtVerifier } from 'aws-jwt-verify'
import { UnauthorizedError } from './errors'

/**
 * Verifies the Cognito ID token (not the access token — only the ID token reliably carries the
 * `email` claim the link flow needs, and using one token type everywhere keeps this a single
 * code path; see the equivalent note on CognitoJwtValidator.java). `aws-jwt-verify` handles
 * JWKS fetch/cache and signature/issuer/audience/expiry checks — the whole reason this is
 * simpler than the Java Nimbus-based implementation, which had to wire all of that by hand
 * since no purpose-built Cognito JWT library exists for Java.
 */
const verifier = CognitoJwtVerifier.create({
  userPoolId: process.env.COGNITO_USER_POOL_ID ?? '',
  tokenUse: 'id',
  clientId: process.env.COGNITO_CLIENT_ID ?? null,
})

export interface CognitoClaims {
  sub: string
  email?: string
}

function bearerToken(headers: Record<string, string | undefined> | undefined): string {
  const header = headers
    ? Object.entries(headers).find(([k]) => k.toLowerCase() === 'authorization')?.[1]
    : undefined
  if (!header || !header.toLowerCase().startsWith('bearer ')) {
    throw new UnauthorizedError('Missing or malformed Authorization header')
  }
  return header.slice(7).trim()
}

export async function verifyRequest(headers: Record<string, string | undefined> | undefined): Promise<CognitoClaims> {
  const token = bearerToken(headers)
  try {
    const payload = await verifier.verify(token)
    if (typeof payload.sub !== 'string') {
      throw new UnauthorizedError('Token missing sub claim')
    }
    return { sub: payload.sub, email: typeof payload.email === 'string' ? payload.email : undefined }
  } catch (e) {
    if (e instanceof UnauthorizedError) throw e
    throw new UnauthorizedError(`Invalid token: ${e instanceof Error ? e.message : String(e)}`)
  }
}
