<script setup lang="ts">
import { DialogPortal, DialogOverlay, DialogContent, DialogClose, DialogTitle } from 'radix-vue'
import { X } from 'lucide-vue-next'
import { cn } from '@/lib/utils'

// A side drawer built on the same radix-vue Dialog primitives as `ui/dialog`. The slide-in is a
// plain CSS transition keyed off radix's data-state attribute — this project has no
// tailwindcss-animate plugin, so the `animate-in`/`slide-in-*` utilities are unavailable.
const props = withDefaults(defineProps<{ side?: 'left' | 'right'; title: string; class?: string }>(), {
  side: 'left',
})
</script>

<template>
  <DialogPortal>
    <DialogOverlay class="sheet-overlay fixed inset-0 z-50 bg-black/50" />
    <DialogContent
      :class="cn(
        'sheet-content fixed inset-y-0 z-50 flex w-72 max-w-[85vw] flex-col border-border bg-background shadow-lg focus:outline-none',
        side === 'left' ? 'left-0 border-r' : 'right-0 border-l',
        props.class,
      )"
      :data-side="side"
    >
      <!-- Radix requires a title for screen readers; visually the slot renders its own header -->
      <DialogTitle class="sr-only">{{ title }}</DialogTitle>
      <slot />
      <DialogClose
        class="absolute right-3 top-3 rounded-sm p-1 opacity-70 transition-opacity hover:opacity-100 focus:outline-none focus:ring-1 focus:ring-ring"
        aria-label="Close menu"
      >
        <X class="h-4 w-4" />
      </DialogClose>
    </DialogContent>
  </DialogPortal>
</template>

<style scoped>
@keyframes sheet-fade-in { from { opacity: 0 } to { opacity: 1 } }
@keyframes sheet-fade-out { from { opacity: 1 } to { opacity: 0 } }
@keyframes sheet-in-left { from { transform: translateX(-100%) } to { transform: translateX(0) } }
@keyframes sheet-out-left { from { transform: translateX(0) } to { transform: translateX(-100%) } }
@keyframes sheet-in-right { from { transform: translateX(100%) } to { transform: translateX(0) } }
@keyframes sheet-out-right { from { transform: translateX(0) } to { transform: translateX(100%) } }

.sheet-overlay[data-state='open'] { animation: sheet-fade-in 150ms ease-out; }
.sheet-overlay[data-state='closed'] { animation: sheet-fade-out 150ms ease-in; }
.sheet-content[data-side='left'][data-state='open'] { animation: sheet-in-left 200ms ease-out; }
.sheet-content[data-side='left'][data-state='closed'] { animation: sheet-out-left 200ms ease-in; }
.sheet-content[data-side='right'][data-state='open'] { animation: sheet-in-right 200ms ease-out; }
.sheet-content[data-side='right'][data-state='closed'] { animation: sheet-out-right 200ms ease-in; }

@media (prefers-reduced-motion: reduce) {
  .sheet-overlay, .sheet-content { animation: none !important; }
}
</style>
