import { createHmac, timingSafeEqual } from 'node:crypto'
import { ValidationError } from '../errors'

/**
 * Mints/verifies the OAuth `state` query parameter as an HMAC-SHA256-signed, base64url payload —
 * `nonce:issuedAt:playlistId:signature`. Bit-for-bit the same design as the Spring port's
 * `SpotifyStateService.java`: the acting contributor is deliberately NOT encoded here, since
 * both `spotify-connect` and `spotify-callback` are `authenticated-with-contributor` functions
 * that resolve the contributor from the caller's own Cognito token (via `withAuth`) — `state`
 * only needs to (a) survive the Spotify round trip carrying which playlist to redirect back to
 * and (b) prove it was minted by this server and hasn't expired. A CSRF check, not an identity
 * carrier.
 */

const TTL_SECONDS = 10 * 60

function secretKey(): Buffer {
  const hex = process.env.SPOTIFY_STATE_SECRET ?? '0000000000000000'
  return Buffer.from(hex, 'hex')
}

function sign(payload: string): string {
  return createHmac('sha256', secretKey()).update(payload).digest('hex')
}

export function mintSpotifyState(playlistId: string | undefined): string {
  const nonce = Math.floor(Math.random() * Number.MAX_SAFE_INTEGER)
  const issuedAt = Math.floor(Date.now() / 1000)
  const payload = `${nonce}:${issuedAt}:${playlistId ?? ''}`
  const raw = `${payload}:${sign(payload)}`
  return Buffer.from(raw, 'utf8').toString('base64url')
}

/** @returns the `playlistId` carried in the state, or `undefined` if none was set. */
export function verifySpotifyState(state: string): string | undefined {
  let raw: string
  try {
    raw = Buffer.from(state, 'base64url').toString('utf8')
  } catch {
    throw new ValidationError(['state: malformed'])
  }
  const parts = raw.split(':')
  if (parts.length !== 4) throw new ValidationError(['state: malformed'])
  const [nonce, issuedAtRaw, playlistId, signature] = parts
  const payload = `${nonce}:${issuedAtRaw}:${playlistId}`
  const expected = sign(payload)
  const signatureBuf = Buffer.from(signature, 'utf8')
  const expectedBuf = Buffer.from(expected, 'utf8')
  if (signatureBuf.length !== expectedBuf.length || !timingSafeEqual(signatureBuf, expectedBuf)) {
    throw new ValidationError(['state: invalid signature'])
  }
  const issuedAt = Number(issuedAtRaw)
  if (!Number.isFinite(issuedAt) || Math.floor(Date.now() / 1000) - issuedAt > TTL_SECONDS) {
    throw new ValidationError(['state: expired'])
  }
  return playlistId === '' ? undefined : playlistId
}
