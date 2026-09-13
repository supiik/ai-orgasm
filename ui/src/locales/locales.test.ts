import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

// `en.json` is the reference catalogue; every other locale must define exactly the same keys,
// with the same `{placeholders}` in each message. Read raw from disk (not via import) so the
// unplugin-vue-i18n transform doesn't turn message strings into compiled functions.

const dir = __dirname
const files = readdirSync(dir).filter(f => f.endsWith('.json'))
const catalogues = Object.fromEntries(
  files.map(f => [f.replace(/\.json$/, ''), JSON.parse(readFileSync(join(dir, f), 'utf-8'))]),
) as Record<string, Record<string, unknown>>

function flatten(obj: Record<string, unknown>, prefix = ''): Record<string, string> {
  return Object.entries(obj).reduce<Record<string, string>>((acc, [k, v]) => {
    const key = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object') Object.assign(acc, flatten(v as Record<string, unknown>, key))
    else acc[key] = String(v)
    return acc
  }, {})
}

// Distinct names only — plural messages repeat `{n}` once per form, and locales differ in how
// many forms they have (Slovak has four, English three).
const placeholders = (msg: string) => [...new Set([...msg.matchAll(/\{(\w+)\}/g)].map(m => m[1]))].sort()

const reference = flatten(catalogues.en)
const others = Object.keys(catalogues).filter(l => l !== 'en')

describe('locale catalogues', () => {
  it('has at least one translation besides English', () => {
    expect(others.length).toBeGreaterThan(0)
  })

  it.each(others)('%s has exactly the keys of en', (locale) => {
    const keys = Object.keys(flatten(catalogues[locale])).sort()
    expect(keys).toEqual(Object.keys(reference).sort())
  })

  it.each(others)('%s uses the same {placeholders} as en', (locale) => {
    const flat = flatten(catalogues[locale])
    for (const [key, msg] of Object.entries(reference)) {
      expect(placeholders(flat[key]), key).toEqual(placeholders(msg))
    }
  })

  it('has no empty messages', () => {
    for (const [locale, cat] of Object.entries(catalogues)) {
      for (const [key, msg] of Object.entries(flatten(cat))) {
        expect(msg.trim(), `${locale}:${key}`).not.toBe('')
      }
    }
  })
})
