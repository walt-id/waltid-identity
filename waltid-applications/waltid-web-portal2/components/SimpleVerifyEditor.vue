<script setup lang="ts">
import type { useVerifierSession } from '~/composables/useVerifierSession'

const props = defineProps<{
  session: ReturnType<typeof useVerifierSession>
}>()

const selectedOptionId = ref(SIMPLE_CREDENTIAL_OPTIONS[0]!.id)
const selectedClaimIds = ref<string[]>([])

const selectedOption = computed(() => getSimpleCredentialOption(selectedOptionId.value))

watch(
  selectedOption,
  (option) => {
    selectedClaimIds.value = option.verifier.claims.map(claim => claim.id)
  },
  { immediate: true },
)

const selectedClaims = computed(() =>
  selectedOption.value.verifier.claims.filter(claim => selectedClaimIds.value.includes(claim.id)),
)

const canSubmit = computed(() => selectedClaims.value.length > 0)
const allClaimsSelected = computed(() => selectedClaimIds.value.length === selectedOption.value.verifier.claims.length)

function selectOption(id: string) {
  selectedOptionId.value = id
}

function selectAllClaims() {
  selectedClaimIds.value = selectedOption.value.verifier.claims.map(claim => claim.id)
}

function deselectAllClaims() {
  selectedClaimIds.value = []
}

function pillClass(tone: string) {
  return {
    'bg-blue-50 text-blue-700 border-blue-200': tone === 'blue',
    'bg-green-50 text-green-700 border-green-200': tone === 'green',
    'bg-purple-50 text-purple-700 border-purple-200': tone === 'purple',
    'bg-slate-50 text-slate-700 border-slate-200': tone === 'slate',
  }
}

async function submit() {
  const option = selectedOption.value
  const credential: Record<string, unknown> = {
    id: option.verifier.credentialId,
    format: option.format,
    meta: option.verifier.meta,
    claims: selectedClaims.value.map(claim => ({
      id: claim.id,
      path: claim.path,
    })),
  }

  await props.session.createSession({
    flow_type: 'cross_device',
    core_flow: {
      dcql_query: {
        credentials: [credential],
      },
    },
  })
}
</script>

<template>
  <div class="grid gap-5">
    <section>
      <h2 class="text-lg font-semibold mb-1">Choose what to verify</h2>
      <p class="text-sm text-[--color-text-muted] mb-3">
        Pick a credential type and choose which information the wallet should present.
      </p>

      <div class="grid md:grid-cols-3 gap-3">
        <button
          v-for="option in SIMPLE_CREDENTIAL_OPTIONS"
          :key="option.id"
          type="button"
          class="text-left rounded-xl border p-4 transition-colors bg-white"
          :class="selectedOptionId === option.id
            ? 'border-slate-900 ring-2 ring-slate-900/10'
            : 'border-[--color-border] hover:border-[--color-border-strong]'"
          @click="selectOption(option.id)"
        >
          <div class="flex items-start justify-between gap-3">
            <div>
              <h3 class="font-semibold text-base">{{ option.title }}</h3>
              <p class="text-xs text-[--color-text-muted] mt-1">{{ option.format }}</p>
            </div>
          </div>

          <p class="text-sm text-[--color-text-secondary] mt-3">{{ option.description }}</p>

          <div class="flex flex-wrap gap-2 mt-4">
            <span
              v-for="pill in option.pills"
              :key="pill.label"
              class="inline-flex rounded-full border px-2.5 py-1 text-xs font-medium"
              :class="pillClass(pill.tone)"
            >
              {{ pill.label }}
            </span>
          </div>
        </button>
      </div>
    </section>

    <section>
      <div class="flex items-start justify-between gap-3 mb-3">
        <div>
          <label class="form-label">Claims to request</label>
          <p class="text-xs text-[--color-text-muted]">
            The verifier will create an unsigned, unencrypted OpenID4VP request over HTTP for the selected claims.
          </p>
        </div>

        <div class="flex shrink-0 gap-2">
          <button
            type="button"
            class="btn btn-secondary !px-3 !py-1.5 !text-xs"
            :disabled="allClaimsSelected"
            @click="selectAllClaims"
          >
            Select all
          </button>
          <button
            type="button"
            class="btn btn-secondary !px-3 !py-1.5 !text-xs"
            :disabled="selectedClaimIds.length === 0"
            @click="deselectAllClaims"
          >
            Deselect all
          </button>
        </div>
      </div>

      <div class="grid sm:grid-cols-2 gap-2">
        <label
          v-for="claim in selectedOption.verifier.claims"
          :key="claim.id"
          class="flex items-start gap-3 rounded-lg border border-[--color-border] bg-white p-3 cursor-pointer hover:border-[--color-border-strong]"
        >
          <input
            v-model="selectedClaimIds"
            type="checkbox"
            :value="claim.id"
            class="mt-1"
          />
          <span>
            <span class="block text-sm font-medium">{{ claim.label }}</span>
          </span>
        </label>
      </div>
    </section>

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
