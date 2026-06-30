<script setup lang="ts">
import type { useSwaggerExamples } from '~/composables/useSwaggerExamples'
import type { useIssuerSession } from '~/composables/useIssuerSession'
import type { useProfiles } from '~/composables/useProfiles'

const props = defineProps<{
  swagger: ReturnType<typeof useSwaggerExamples>
  session: ReturnType<typeof useIssuerSession>
  profiles: ReturnType<typeof useProfiles>
}>()

const json = defineModel<string>('json', { required: true })
const selectedIndex = defineModel<number>('selectedIndex', { default: 0 })

// The profile selected in the dropdown. This overrides the (hardcoded) profileId
// that ships in the Swagger example payload.
const selectedProfile = ref('')

const canSubmit = computed(() => {
  if (!json.value.trim()) return false
  try { JSON.parse(json.value); return true } catch { return false }
})

function readProfileId(raw: string): string | null {
  try {
    const obj = JSON.parse(raw || '{}')
    return typeof obj?.profileId === 'string' ? obj.profileId : null
  } catch {
    return null
  }
}

// Force the payload's profileId to match the dropdown selection.
function applyOverride() {
  if (!selectedProfile.value) return
  try {
    const obj = JSON.parse(json.value || '{}') as Record<string, unknown>
    if (obj.profileId !== selectedProfile.value) {
      obj.profileId = selectedProfile.value
      json.value = JSON.stringify(obj, null, 2)
    }
  } catch {
    // payload not valid JSON — leave it untouched
  }
}

// Initialise the dropdown once profiles + payload are available: prefer the
// profileId already present in the example, otherwise the first profile.
watch(
  [() => props.profiles.profiles.value, json],
  () => {
    const list = props.profiles.profiles.value
    if (selectedProfile.value || list.length === 0) return
    const fromPayload = readProfileId(json.value)
    const matchesPayload = !!fromPayload && list.some(p => p.profileId === fromPayload)
    selectedProfile.value = matchesPayload ? fromPayload! : list[0]!.profileId
    // If the example shipped a profileId we don't recognise, align the payload now.
    if (!matchesPayload) applyOverride()
    props.profiles.loadDetail(selectedProfile.value)
  },
  { immediate: true },
)

// When a different Swagger example is loaded it brings its own hardcoded
// profileId — re-apply the user's override so the dropdown keeps winning.
watch(selectedIndex, () => nextTick(applyOverride))

function onProfileChange(e: Event) {
  selectedProfile.value = (e.target as HTMLSelectElement).value
  applyOverride()
  props.profiles.loadDetail(selectedProfile.value)
}

const selectedProfileDetail = computed(() =>
  selectedProfile.value ? props.profiles.details.value[selectedProfile.value] : null,
)

async function submit() {
  await props.session.createOffer(JSON.parse(json.value))
}
</script>

<template>
  <div class="grid gap-4">
    <div class="grid gap-3">
      <div>
        <label class="form-label">Profile</label>
        <select
          v-if="profiles.profiles.value.length > 0"
          :value="selectedProfile"
          class="form-select"
          @change="onProfileChange"
        >
          <option v-for="p in profiles.profiles.value" :key="p.profileId" :value="p.profileId">
            {{ p.name ? `${p.name} (${p.profileId})` : p.profileId }}
          </option>
        </select>
        <div v-else-if="profiles.loading.value" class="form-select text-[--color-text-muted]">Loading…</div>
        <div v-else-if="profiles.error.value" class="form-select text-red-500 text-xs">{{ profiles.error.value }}</div>
        <div v-else class="form-select text-[--color-text-muted]">No profiles</div>
        <p class="text-xs text-[--color-text-muted] mt-1">
          Overrides the <code>profileId</code> in the payload below.
        </p>
      </div>

      <JsonViewer
        label="Profile definition"
        :value="selectedProfileDetail"
        :loading="profiles.detailLoading.value"
        :error="profiles.detailError.value"
      />
    </div>

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
        Create Offer
      </button>
      <span v-if="session.error.value" class="text-sm text-red-600">{{ session.error.value }}</span>
    </div>
  </div>
</template>
