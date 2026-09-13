// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { detectLocale, readLocaleCookie, writeLocaleCookie, setLocale, i18n, LOCALE_COOKIE } from './i18n'

function clearCookie() {
  document.cookie = `${LOCALE_COOKIE}=; Max-Age=0; Path=/`
}

describe('locale cookie', () => {
  beforeEach(clearCookie)

  it('round-trips through document.cookie', () => {
    expect(readLocaleCookie()).toBeNull()
    writeLocaleCookie('sk')
    expect(document.cookie).toContain('locale=sk')
    expect(readLocaleCookie()).toBe('sk')
  })

  it('ignores an unsupported cookie value', () => {
    document.cookie = `${LOCALE_COOKIE}=xx; Path=/`
    expect(readLocaleCookie()).toBeNull()
  })
})

describe('detectLocale', () => {
  beforeEach(clearCookie)

  it('prefers the cookie over the browser language', () => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['sk-SK', 'sk'])
    writeLocaleCookie('en')
    expect(detectLocale()).toBe('en')
  })

  it('maps a regional browser language to its base locale', () => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['sk-SK', 'en-US'])
    expect(detectLocale()).toBe('sk')
  })

  it('falls back to English for unsupported browser languages', () => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['fr-FR', 'de'])
    expect(detectLocale()).toBe('en')
  })
})

describe('setLocale', () => {
  it('switches the active locale, persists it and updates <html lang>', () => {
    setLocale('sk')
    expect(i18n.global.locale.value).toBe('sk')
    expect(readLocaleCookie()).toBe('sk')
    expect(document.documentElement.lang).toBe('sk')
    expect(i18n.global.t('nav.songs')).toBe('Skladby')
    setLocale('en')
    expect(i18n.global.t('nav.songs')).toBe('Songs')
  })
})

describe('Slovak plurals', () => {
  it('picks zero / one / few / many forms', () => {
    setLocale('sk')
    const t = i18n.global.t
    expect(t('songs.count', 0)).toBe('žiadne skladby')
    expect(t('songs.count', 1)).toBe('1 skladba')
    expect(t('songs.count', 3)).toBe('3 skladby')
    expect(t('songs.count', 5)).toBe('5 skladieb')
    expect(t('songs.count', 21)).toBe('21 skladieb')
    setLocale('en')
    expect(t('songs.count', 0)).toBe('no songs')
    expect(t('songs.count', 1)).toBe('1 song')
    expect(t('songs.count', 2)).toBe('2 songs')
  })
})
