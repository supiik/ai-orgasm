import { computed } from 'vue'
import { i18n, setLocale, SUPPORTED_LOCALES, type Locale } from '@/i18n'

/** Active locale + switcher; the persistence (cookie, `<html lang>`) lives in `@/i18n`. */
export function useLocale() {
  const locale = computed<Locale>({
    get: () => i18n.global.locale.value,
    set: (value) => setLocale(value),
  })
  return { locale, setLocale, supportedLocales: SUPPORTED_LOCALES }
}
