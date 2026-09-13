/**
 * Vue's v-model on <input type="number"> casts the bound value to a JS number once non-empty
 * (documented Vue 3 behavior, applied even without an explicit .number modifier) — despite the
 * form field being typed as `string`. Calling `.trim()` on it directly throws once a value is
 * typed, which silently failed song create/update (caught by a generic try/catch, no request
 * ever sent). String(...) normalizes both possible runtime types before the emptiness check.
 */
export function parseReleaseYear(raw: string | number): number | undefined {
  const trimmed = String(raw).trim()
  return trimmed ? Number(trimmed) : undefined
}
