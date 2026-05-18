<script setup lang="ts">
import { ref, watch } from 'vue'
import { Search } from 'lucide-vue-next'
import { Input } from '@/components/ui/input'

withDefaults(defineProps<{ placeholder?: string }>(), {
  placeholder: 'Filter by name…',
})

const model = defineModel<string>({ default: '' })
const local = ref(model.value)
let timer: ReturnType<typeof setTimeout> | undefined

watch(local, (val) => {
  clearTimeout(timer)
  timer = setTimeout(() => { model.value = val }, 300)
})

watch(model, (val) => {
  if (val !== local.value) local.value = val
})
</script>

<template>
  <div class="relative">
    <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
    <Input v-model="local" :placeholder="placeholder" class="pl-9 w-64" />
  </div>
</template>
