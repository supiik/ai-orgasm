import { randomInt } from 'node:crypto'
import { NotFoundError } from './errors'

/**
 * Port of backend-dynamo's IdGenerator.java (com.orgasm.dynamo.domain.IdGenerator). Java's
 * `long` is a fixed-width 64-bit two's-complement integer; JS `number` only safely represents
 * 53 bits, so every operation here uses `bigint`, explicitly masked to 64 bits after each step
 * to reproduce Java's wraparound semantics (BigInt itself has no fixed width).
 */

const MASK_64 = (1n << 64n) - 1n
const CUSTOM_EPOCH = 1_700_000_000_000n // same custom epoch as the Java source (2023-11-14)
const RANDOM_BITS_MASK = 0x3fffffn // 22 bits

function wrap64(x: bigint): bigint {
  return x & MASK_64
}

/** Bit-for-bit port of java.lang.Long.reverse(long) — no built-in JS/BigInt equivalent. */
function reverse64(x: bigint): bigint {
  let result = 0n
  let value = wrap64(x)
  for (let i = 0; i < 64; i++) {
    result = (result << 1n) | (value & 1n)
    value >>= 1n
  }
  return result
}

// Mirrors app.id.secret (16 hex chars, default all zeros) from IdGeneratorConfig.java.
const secretKey = BigInt(`0x${process.env.ID_GENERATOR_SECRET ?? '0000000000000000'}`)

/**
 * Generates a sortable 64-bit ID: 42 ms bits (from the custom epoch) + 22 random bits. Always
 * non-negative when read back as an unsigned 64-bit value (same guarantee the Java source makes).
 */
export function generateId(): bigint {
  const offsetMs = BigInt(Date.now()) - CUSTOM_EPOCH
  const random = BigInt(randomInt(0, 0x400000)) & RANDOM_BITS_MASK
  return wrap64((offsetMs << 22n) | random)
}

/**
 * Formats a DB id as the user-visible prefixed id. XORs with the secret key then bit-reverses
 * so the DB sequence isn't apparent from the external representation — same scheme as format()
 * in IdGenerator.java.
 */
export function formatId(prefix: string, id: bigint): string {
  const reversed = reverse64(wrap64(id ^ secretKey))
  return `${prefix}-${reversed.toString(16).padStart(16, '0')}`
}

/**
 * Inverse of formatId — parses a prefixed id back to the DB id. Anything that isn't the hex tail
 * formatId produces is rejected as NotFound: without this, `BigInt('0x' + garbage)` threw a raw
 * SyntaxError, which withAuth's catch chain surfaced as a 500 rather than a client error.
 */
export function parseId(prefixedId: string): bigint {
  const hex = prefixedId.slice(prefixedId.lastIndexOf('-') + 1)
  if (!/^[0-9a-fA-F]{1,16}$/.test(hex)) {
    throw new NotFoundError('Malformed id')
  }
  return wrap64(reverse64(BigInt(`0x${hex}`)) ^ secretKey)
}
