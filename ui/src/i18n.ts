import { createI18n } from 'vue-i18n'
import en from './locales/en.json'
import sk from './locales/sk.json'

// Message catalogue shape — `en.json` is the reference; every other locale must mirror its keys
// (enforced by `locales/locales.test.ts`). Augmenting `DefineLocaleMessage` gives `t()`/`$t()`
// key autocompletion everywhere without importing the schema in each component.
export type MessageSchema = typeof en

declare module 'vue-i18n' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  export interface DefineLocaleMessage extends MessageSchema {}
}

export const SUPPORTED_LOCALES = ['en', 'sk'] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]
export const DEFAULT_LOCALE: Locale = 'en'

// ── Cookie persistence ─────────────────────────────────────────────────────────
// A cookie rather than localStorage (which `useTheme` uses) because the choice is then also
// visible to the server side — nginx / the backend can read it for localized responses later
// without a second mechanism. One year, SameSite=Lax; `Secure` whenever the page itself is https.

export const LOCALE_COOKIE = 'locale'
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365

function isSupported(value: string | null | undefined): value is Locale {
  return !!value && (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

export function readLocaleCookie(): Locale | null {
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${LOCALE_COOKIE}=([^;]*)`))
  const value = match ? decodeURIComponent(match[1]) : null
  return isSupported(value) ? value : null
}

export function writeLocaleCookie(locale: Locale) {
  const secure = location.protocol === 'https:' ? '; Secure' : ''
  document.cookie = `${LOCALE_COOKIE}=${locale}; Max-Age=${COOKIE_MAX_AGE}; Path=/; SameSite=Lax${secure}`
}

/** Cookie first, then the browser's preferred languages (`sk-SK` → `sk`), then the default. */
export function detectLocale(): Locale {
  const fromCookie = readLocaleCookie()
  if (fromCookie) return fromCookie
  for (const lang of navigator.languages ?? [navigator.language]) {
    const base = lang?.split('-')[0]?.toLowerCase()
    if (isSupported(base)) return base
  }
  return DEFAULT_LOCALE
}

// ── Plural rules ───────────────────────────────────────────────────────────────
// Slovak has four plural forms — message strings are `zero | one | few (2–4) | many (5+)`.
// English keeps vue-i18n's built-in `zero | one | other` rule.

function slovakPlural(choice: number, choicesLength: number): number {
  const index = choice === 0 ? 0 : choice === 1 ? 1 : choice >= 2 && choice <= 4 ? 2 : 3
  return Math.min(index, choicesLength - 1)
}

// ── Date formats ───────────────────────────────────────────────────────────────
// Named formats used via `d(value, 'date')` / `d(value, 'dateTime')`; identical per locale — Intl
// does the locale-specific rendering.

const dateFormats = {
  date: { dateStyle: 'medium' },
  dateTime: { dateStyle: 'medium', timeStyle: 'short' },
} as const

export const i18n = createI18n<[MessageSchema], Locale>({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages: { en, sk },
  pluralRules: { sk: slovakPlural },
  datetimeFormats: { en: dateFormats, sk: dateFormats },
})

/** Switches the active locale, persists it to the cookie and mirrors it onto `<html lang>`. */
export function setLocale(locale: Locale) {
  i18n.global.locale.value = locale
  writeLocaleCookie(locale)
  document.documentElement.lang = locale
}

document.documentElement.lang = i18n.global.locale.value
