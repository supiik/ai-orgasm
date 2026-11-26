// Light / dark / system theme is shared with aquarium and the admin apps (@supiik/pico-ui/colorTheme):
// same localStorage key (`theme`) and `dark` class on <html>. orgasm keeps its own cycle order.
import { colorTheme, setColorTheme } from '@supiik/pico-ui/colorTheme'

export function useTheme() {
  function cycle() {
    const t = colorTheme.value
    setColorTheme(t === 'system' ? 'light' : t === 'light' ? 'dark' : 'system')
  }
  return { theme: colorTheme, cycle }
}
