<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

const status = ref<string>('Loading…')

onMounted(async () => {
  try {
    const { data } = await axios.get<{ success: boolean; data: Record<string, string> }>('/api/health')
    status.value = data.data?.status ?? 'UP'
  } catch {
    status.value = 'Backend unreachable'
  }
})
</script>

<template>
  <section>
    <h1 class="text-2xl font-semibold mb-2">Anchor</h1>
    <p class="text-muted-foreground">Backend status: <strong class="text-foreground">{{ status }}</strong></p>
  </section>
</template>
