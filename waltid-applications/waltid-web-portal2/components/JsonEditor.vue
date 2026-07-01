<script setup lang="ts">
import type { SwaggerExample } from '~/composables/useSwaggerExamples'

const props = defineProps<{
  examples: SwaggerExample[]
  loading: boolean
  error: string | null
  optionsLabel: string
  warning?: string | null
}>()

defineEmits<{ reload: [] }>()

const json = defineModel<string>({ default: '' })
const selectedIndex = defineModel<number>('selectedIndex', { default: 0 })

function onSelectChange(e: Event) {
  const idx = Number((e.target as HTMLSelectElement).value)
  selectedIndex.value = idx
  const ex = props.examples[idx]
  if (ex) json.value = JSON.stringify(ex.payload, null, 2)
}
</script>

<template>
  <div class="grid gap-3">
    <div class="flex items-end gap-2">
      <div class="flex-1">
        <label class="form-label">{{ optionsLabel }}</label>
        <select
          v-if="examples.length > 0"
          :value="selectedIndex"
          class="form-select"
          @change="onSelectChange"
        >
          <option v-for="(ex, i) in examples" :key="ex.title" :value="i">
            {{ ex.title }}
          </option>
        </select>
        <div v-else-if="loading" class="form-select text-[--color-text-muted]">Loading…</div>
        <div v-else-if="error" class="form-select text-red-500 text-xs">{{ error }}</div>
        <div v-else class="form-select text-[--color-text-muted]">No examples</div>
      </div>

      <button class="btn btn-secondary" :disabled="loading" @click="$emit('reload')">
        Reload Options
      </button>
    </div>

    <div>
      <p v-if="warning" class="mb-2 text-xs text-red-600">
        {{ warning }}
      </p>
      <label class="form-label">Payload (editable JSON)</label>
      <textarea
        v-model="json"
        class="form-textarea"
        spellcheck="false"
        placeholder="{}"
      />
    </div>
  </div>
</template>
