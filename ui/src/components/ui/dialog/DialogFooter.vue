<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{ class?: string }>()
</script>

<template>
  <div :class="cn('flex justify-end gap-2 pt-4', props.class)">
    <slot />
  </div>
</template>
