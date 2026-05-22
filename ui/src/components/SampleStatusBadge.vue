<script setup lang="ts">
import type { SampleStatus } from '@pi2/anchor-client'

defineProps<{ status: SampleStatus }>()

const styles: Record<string, string> = {
  DRAFT:    'bg-muted text-muted-foreground',
  ACTIVE:   'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  ARCHIVED: 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200',
}
</script>

<template>
  <span
    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
    :class="styles[status] ?? 'bg-muted text-muted-foreground'"
  >
    {{ status }}
  </span>
</template>
