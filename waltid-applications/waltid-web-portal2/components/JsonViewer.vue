<script setup lang="ts">
const props = defineProps<{
  label: string
  value: unknown
  loading?: boolean
  error?: string | null
  defaultOpen?: boolean
}>()

const open = ref(props.defaultOpen ?? false)

const formatted = computed(() => {
  if (props.value === null || props.value === undefined) return ''
  try {
    return JSON.stringify(props.value, null, 2)
  } catch {
    return String(props.value)
  }
})
</script>

<template>
  <div class="rounded-lg border border-[--color-border]">
    <button
      type="button"
      class="w-full flex items-center justify-between px-3 py-2 text-left"
      @click="open = !open"
    >
      <span class="text-sm font-semibold">{{ label }}</span>
      <svg
        class="h-4 w-4 transition-transform text-[--color-text-muted]"
        :class="open ? 'rotate-90' : ''"
        viewBox="0 0 20 20"
        fill="currentColor"
      >
        <path fill-rule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clip-rule="evenodd" />
      </svg>
    </button>

    <div v-if="open" class="px-3 pb-3">
      <div v-if="loading" class="text-sm text-[--color-text-muted]">Loading…</div>
      <div v-else-if="error" class="text-sm text-red-500">{{ error }}</div>
      <pre v-else-if="formatted" class="log-box max-h-80 text-xs">{{ formatted }}</pre>
      <div v-else class="text-sm text-[--color-text-muted]">No data</div>
    </div>
  </div>
</template>
