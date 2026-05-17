<script setup lang="ts">
import { DialogRoot } from 'radix-vue'

const open = defineModel<boolean>('open', { default: false })
</script>

<template>
  <DialogRoot v-model:open="open">
    <slot />
  </DialogRoot>
</template>
