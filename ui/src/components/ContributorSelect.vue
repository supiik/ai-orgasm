<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { PopoverRoot, PopoverTrigger, PopoverPortal, PopoverContent } from 'radix-vue'
import { ChevronDown, Check, Search } from 'lucide-vue-next'
import { cn } from '@/lib/utils'

interface Contributor {
  id: string
  name: string
  avatarUrl: string | null
}

const props = defineProps<{
  contributors: Contributor[]
  placeholder?: string
  disabled?: boolean
  class?: string
}>()

const model = defineModel<string>()

const open = ref(false)
const search = ref('')
const searchInputRef = ref<HTMLInputElement | null>(null)
const itemRefs = ref<HTMLButtonElement[]>([])

const selected = computed(() => props.contributors.find(c => c.id === model.value) ?? null)

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return props.contributors
  return props.contributors.filter(c => c.name.toLowerCase().includes(q))
})

async function onOpen(val: boolean) {
  open.value = val
  if (val) {
    search.value = ''
    await nextTick()
    searchInputRef.value?.focus()
  }
}

function select(id: string) {
  model.value = id
  open.value = false
}

function onSearchKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    itemRefs.value[0]?.focus()
  } else if (e.key === 'Escape') {
    open.value = false
  }
}

function onItemKeydown(e: KeyboardEvent, index: number) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    itemRefs.value[index + 1]?.focus()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (index === 0) searchInputRef.value?.focus()
    else itemRefs.value[index - 1]?.focus()
  } else if (e.key === 'Escape') {
    open.value = false
  }
}
</script>

<template>
  <PopoverRoot :open="open" @update:open="onOpen">
    <PopoverTrigger as-child>
      <button
        type="button"
        :disabled="props.disabled"
        :class="cn(
          'flex h-9 w-full items-center justify-between rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm',
          'focus:outline-none focus:ring-1 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
          !selected && 'text-muted-foreground',
          props.class,
        )"
      >
        <span class="flex items-center gap-2 min-w-0">
          <template v-if="selected">
            <img
              v-if="selected.avatarUrl"
              :src="selected.avatarUrl"
              :alt="selected.name"
              class="w-5 h-5 rounded-full object-cover shrink-0"
            />
            <div v-else class="w-5 h-5 rounded-full bg-muted shrink-0" />
            <span class="truncate">{{ selected.name }}</span>
          </template>
          <template v-else>{{ placeholder ?? 'Select contributor…' }}</template>
        </span>
        <ChevronDown class="h-4 w-4 opacity-50 shrink-0 ml-2" />
      </button>
    </PopoverTrigger>

    <PopoverPortal>
      <PopoverContent
        align="start"
        :side-offset="4"
        class="z-50 min-w-48 w-[var(--radix-popover-trigger-width)] overflow-hidden rounded-md border border-border bg-popover text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 p-0"
      >
        <div class="flex items-center border-b border-border px-3 gap-2">
          <Search class="h-4 w-4 opacity-50 shrink-0" />
          <input
            ref="searchInputRef"
            v-model="search"
            class="flex h-9 w-full bg-transparent py-2 text-sm outline-none placeholder:text-muted-foreground"
            placeholder="Search…"
            @keydown="onSearchKeydown"
          />
        </div>

        <div class="max-h-52 overflow-y-auto p-1">
          <p v-if="!filtered.length" class="py-4 text-center text-sm text-muted-foreground">
            No contributors found.
          </p>
          <button
            v-for="(c, i) in filtered"
            :key="c.id"
            :ref="el => { if (el) itemRefs[i] = el as HTMLButtonElement }"
            type="button"
            :class="cn(
              'relative flex w-full cursor-default select-none items-center gap-2 rounded-sm py-1.5 pl-2 pr-8 text-sm outline-none',
              'hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground',
            )"
            @click="select(c.id)"
            @keydown="onItemKeydown($event, i)"
          >
            <img
              v-if="c.avatarUrl"
              :src="c.avatarUrl"
              :alt="c.name"
              class="w-6 h-6 rounded-full object-cover shrink-0"
            />
            <div v-else class="w-6 h-6 rounded-full bg-muted shrink-0" />
            <span class="truncate">{{ c.name }}</span>
            <Check v-if="c.id === model" class="absolute right-2 h-4 w-4 shrink-0" />
          </button>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
