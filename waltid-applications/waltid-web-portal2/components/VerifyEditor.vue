<script setup lang="ts">
import type { useSwaggerExamples } from '~/composables/useSwaggerExamples'
import type { useVerifierSession } from '~/composables/useVerifierSession'

const props = defineProps<{
  swagger: ReturnType<typeof useSwaggerExamples>
  session: ReturnType<typeof useVerifierSession>
}>()

const json = defineModel<string>('json', { required: true })
const selectedIndex = defineModel<number>('selectedIndex', { default: 0 })

const keyJson = ref('')
const x5cValues = ref([''])
const signedRequest = ref(false)
const encryptedResponse = ref(false)
const optionsError = ref<string | null>(null)

const canSubmit = computed(() => {
  if (!json.value.trim()) return false
  try { JSON.parse(json.value); return true } catch { return false }
})

function readPayload(): Record<string, unknown> {
  const parsed = JSON.parse(json.value || '{}')
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('Request body must be a JSON object.')
  }
  return parsed as Record<string, unknown>
}

function getCoreFlowObject(payload: Record<string, unknown>): Record<string, unknown> {
  const coreFlow = payload.core_flow
  if (coreFlow && typeof coreFlow === 'object' && !Array.isArray(coreFlow)) {
    return coreFlow as Record<string, unknown>
  }

  payload.core_flow = {}
  return payload.core_flow as Record<string, unknown>
}

function addX5cInput() {
  x5cValues.value.push('')
}

function removeX5cInput(index: number) {
  x5cValues.value.splice(index, 1)
  if (x5cValues.value.length === 0) x5cValues.value.push('')
}

function applySecurityOverridesToJson() {
  optionsError.value = null

  let payload: Record<string, unknown>
  try {
    payload = readPayload()
  } catch (e) {
    optionsError.value = e instanceof Error ? e.message : 'Request body must be valid JSON.'
    return null
  }

  const coreFlow = getCoreFlowObject(payload)
  coreFlow.signed_request = signedRequest.value
  coreFlow.encrypted_response = encryptedResponse.value

  if (keyJson.value.trim()) {
    try {
      coreFlow.key = parseJwkKey(keyJson.value)
    } catch (e) {
      optionsError.value = e instanceof Error ? e.message : 'Key must be valid JSON.'
      return null
    }
  } else {
    delete coreFlow.key
  }

  try {
    const x5c = parseVerifierX5c(x5cValues.value)
    if (x5c.length > 0) {
      coreFlow.x5c = x5c
    } else {
      delete coreFlow.x5c
    }
  } catch (e) {
    optionsError.value = e instanceof Error ? e.message : 'Invalid x5c certificate.'
    return null
  }

  json.value = JSON.stringify(payload, null, 2)
  return payload
}

watch([keyJson, x5cValues, signedRequest, encryptedResponse], () => {
  applySecurityOverridesToJson()
}, { deep: true })

watch(selectedIndex, () => nextTick(applySecurityOverridesToJson))

async function submit() {
  const payload = applySecurityOverridesToJson()
  if (!payload) return

  await props.session.createSession(payload)
}
</script>

<template>
  <div class="grid gap-4">
    <JsonEditor
      v-model="json"
      v-model:selected-index="selectedIndex"
      options-label="Verification Session Options"
      :examples="swagger.examples.value"
      :loading="swagger.loading.value"
      :error="swagger.error.value"
      @reload="swagger.load()"
    />

    <details class="group rounded-xl border border-[--color-border] bg-white">
      <summary class="cursor-pointer list-none p-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <span class="text-base font-semibold">Request security options</span>
            <span class="block text-sm text-[--color-text-muted] mt-1">
              These controls override the selected request payload before the verification session is created.
            </span>
          </div>
          <div class="inline-flex items-center gap-2 text-sm font-medium text-[--color-text-muted]">
            <span class="group-open:hidden">Expand</span>
            <span class="hidden group-open:inline">Collapse</span>
            <svg class="h-4 w-4 transition-transform group-open:rotate-180" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.17l3.71-3.94a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
            </svg>
          </div>
        </div>
      </summary>

      <div class="grid gap-4 px-4 pb-4">
        <div class="flex flex-wrap gap-4">
        <label class="inline-flex items-center gap-2 text-sm font-medium">
          <input v-model="signedRequest" type="checkbox" />
          signed_request
        </label>
        <label class="inline-flex items-center gap-2 text-sm font-medium">
          <input v-model="encryptedResponse" type="checkbox" />
          encrypted_response
        </label>
        </div>

        <div>
          <label class="form-label">Key (JWK JSON)</label>
          <textarea
            v-model="keyJson"
            class="form-textarea min-h-[180px]"
            spellcheck="false"
            placeholder='{"type":"jwk","jwk":{"kty":"EC","crv":"P-256","x":"...","y":"...","d":"..."}}'
          />
        </div>

        <div class="grid gap-2">
          <label class="form-label">x5c certificates</label>
          <div
            v-for="(_, index) in x5cValues"
            :key="index"
            class="flex gap-2"
          >
            <input
              v-model="x5cValues[index]"
              class="form-input"
              :placeholder="`Certificate ${index + 1}`"
            />
            <button
              type="button"
              class="btn btn-secondary shrink-0"
              :disabled="x5cValues.length === 1 && !x5cValues[0]"
              @click="removeX5cInput(index)"
            >
              Remove
            </button>
          </div>
          <button type="button" class="btn btn-secondary justify-self-start" @click="addX5cInput">
            Add certificate
          </button>
        </div>

        <p v-if="optionsError" class="text-sm text-red-600">{{ optionsError }}</p>
      </div>
    </details>

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
