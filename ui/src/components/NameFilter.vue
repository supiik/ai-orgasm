<script setup lang="ts">
import { ref, watch } from 'vue'
import { Search } from 'lucide-vue-next'
import { Input } from '@/components/ui/input'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ placeholder?: string }>()
const { t } = useI18n()

const model = defineModel<string>({ default: '' })
const local = ref(model.value)
let timer: ReturnType<typeof setTimeout> | undefined

// The emitted value is trimmed so a trailing space ("creep ") never becomes part of the
// server-side substring match; `local` keeps the raw text so typing isn't disturbed.
watch(local, (val) => {
  clearTimeout(timer)
  timer = setTimeout(() => { model.value = val.trim() }, 300)
})

watch(model, (val) => {
  if (val !== local.value.trim()) local.value = val
})
</script>

<template>
  <div class="relative">
    <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
    <Input v-model="local" :placeholder="props.placeholder ?? t('common.filterByName')" class="pl-9 w-64" />
  </div>
</template>
