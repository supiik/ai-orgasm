<script setup lang="ts">
import type { PlaylistStatus } from '@orgasm/backend-client'
import { cn } from '@/lib/utils'

const props = defineProps<{ status: PlaylistStatus }>()

const config: Record<PlaylistStatus, { label: string; class: string }> = {
  NEW:              { label: 'New',              class: 'bg-secondary text-secondary-foreground' },
  OPEN:             { label: 'Open',             class: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200' },
  GUESSING:         { label: 'Guessing',         class: 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200' },
  UNDER_EVALUATION: { label: 'Under Evaluation', class: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200' },
  CLOSED:           { label: 'Closed',           class: 'bg-muted text-muted-foreground line-through' },
  PUBLISHED:        { label: 'Published',        class: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' },
}
</script>

<template>
  <span :class="cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', config[props.status].class)">
    {{ config[props.status].label }}
  </span>
</template>
