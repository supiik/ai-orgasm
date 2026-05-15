<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

const status = ref<string>('Loading…')

onMounted(async () => {
  try {
    const { data } = await axios.get<{ status: string }>('/api/health')
    status.value = data.status
  } catch {
    status.value = 'Backend unreachable'
  }
})
</script>

<template>
  <section>
    <h1>Orgasm</h1>
    <p>Backend status: <strong>{{ status }}</strong></p>
  </section>
</template>
