<script setup lang="ts">
import type { PlaylistStatus } from '@orgasm/backend-client'
import { cn } from '@/lib/utils'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ status: PlaylistStatus }>()
const { t } = useI18n()

// Labels live in the locale files under `playlistStatus.<STATUS>`.
const config: Record<PlaylistStatus, string> = {
  NEW:              'bg-secondary text-secondary-foreground',
  OPEN:             'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  GUESSING:         'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200',
  UNDER_EVALUATION: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  CLOSED:           'bg-muted text-muted-foreground line-through',
  PUBLISHED:        'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
}
</script>

<template>
  <span :class="cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', config[props.status])">
    {{ t(`playlistStatus.${props.status}`) }}
  </span>
</template>
