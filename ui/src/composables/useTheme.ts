import { ref, watchEffect } from 'vue'

type Theme = 'system' | 'light' | 'dark'

const theme = ref<Theme>((localStorage.getItem('theme') as Theme) ?? 'system')

const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')

function apply(t: Theme) {
  const dark = t === 'dark' || (t === 'system' && mediaQuery.matches)
  document.documentElement.classList.toggle('dark', dark)
}

mediaQuery.addEventListener('change', () => {
  if (theme.value === 'system') apply('system')
})

watchEffect(() => {
  if (theme.value === 'system') localStorage.removeItem('theme')
  else localStorage.setItem('theme', theme.value)
  apply(theme.value)
})

export function useTheme() {
  function cycle() {
    theme.value = theme.value === 'system' ? 'light' : theme.value === 'light' ? 'dark' : 'system'
  }
  return { theme, cycle }
}
