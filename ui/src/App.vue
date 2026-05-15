<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <header>
    <nav>
      <RouterLink to="/">Home</RouterLink>
    </nav>
  </header>
  <main>
    <RouterView />
  </main>
</template>

<style scoped>
header {
  padding: 1rem;
  background-color: #1a1a2e;
}

nav a {
  color: #e0e0e0;
  text-decoration: none;
  margin-right: 1rem;
}

nav a.router-link-active {
  color: #42b883;
}

main {
  padding: 2rem;
}
</style>
