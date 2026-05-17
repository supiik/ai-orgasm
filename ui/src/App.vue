<script setup lang="ts">
import { RouterView, RouterLink } from 'vue-router'
import { ListMusic } from 'lucide-vue-next'
</script>

<template>
  <div class="flex h-screen bg-background text-foreground">

    <!-- Sidebar -->
    <aside class="w-56 shrink-0 border-r border-border flex flex-col">
      <div class="h-14 flex items-center px-4 border-b border-border font-semibold text-sm tracking-wide">
        Orgasm
      </div>
      <nav class="flex-1 px-2 py-3 space-y-0.5">
        <RouterLink
          to="/playlists"
          class="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          active-class="bg-accent text-accent-foreground font-medium"
        >
          <ListMusic class="h-4 w-4 shrink-0" />
          Playlists
        </RouterLink>
      </nav>
    </aside>

    <!-- Main content -->
    <main class="flex-1 overflow-y-auto p-6">
      <RouterView />
    </main>

  </div>
</template>
