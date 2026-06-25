<script setup lang="ts">
import type { useSwaggerExamples } from '~/composables/useSwaggerExamples'
import type { useVerifierSession } from '~/composables/useVerifierSession'

const props = defineProps<{
  swagger: ReturnType<typeof useSwaggerExamples>
  session: ReturnType<typeof useVerifierSession>
}>()

const json = defineModel<string>('json', { required: true })
const selectedIndex = defineModel<number>('selectedIndex', { default: 0 })

const canSubmit = computed(() => {
  if (!json.value.trim()) return false
  try { JSON.parse(json.value); return true } catch { return false }
})

async function submit() {
  await props.session.createSession(JSON.parse(json.value))
}
</script>

<template>
  <div class="grid gap-4">
    <JsonEditor
      v-model="json"
      v-model:selected-index="selectedIndex"
      :examples="swagger.examples.value"
      :loading="swagger.loading.value"
      :error="swagger.error.value"
      @reload="swagger.load()"
    />

    <div class="flex items-center gap-3">
      <button
        class="btn btn-primary"
        :disabled="!canSubmit || session.loading.value"
        @click="submit"
      >
        <svg v-if="session.loading.value" class="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
        </svg>
        Create Verification Session
      </button>
      <span v-if="session.error.value" class="text-sm text-red-600">{{ session.error.value }}</span>
    </div>
  </div>
</template>
