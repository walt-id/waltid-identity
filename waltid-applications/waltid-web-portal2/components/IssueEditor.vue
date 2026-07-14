<script setup lang="ts">
import type { useSwaggerExamples } from "~/composables/useSwaggerExamples";
import type { useIssuerSession } from "~/composables/useIssuerSession";
import type { useProfiles } from "~/composables/useProfiles";

const props = defineProps<{
  swagger: ReturnType<typeof useSwaggerExamples>;
  session: ReturnType<typeof useIssuerSession>;
  profiles: ReturnType<typeof useProfiles>;
}>();

const json = defineModel<string>("json", { required: true });
const selectedIndex = defineModel<number>("selectedIndex", { default: 0 });

const keyJson = ref("");
const x5ChainValues = ref([""]);
const optionsError = ref<string | null>(null);

// The profile selected in the dropdown. This overrides the (hardcoded) profileId
// that ships in the Swagger example payload.
const selectedProfile = ref("");

const canSubmit = computed(() => {
  if (!json.value.trim()) return false;
  try {
    JSON.parse(json.value);
    return true;
  } catch {
    return false;
  }
});
const submitDisabled = computed(
  () => !canSubmit.value || !!optionsError.value || props.session.loading.value,
);

function readPayload(): Record<string, unknown> {
  const parsed = JSON.parse(json.value || "{}");
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("Request body must be a JSON object.");
  }
  return parsed as Record<string, unknown>;
}

function readProfileId(raw: string): string | null {
  try {
    const obj = JSON.parse(raw || "{}");
    return typeof obj?.profileId === "string" ? obj.profileId : null;
  } catch {
    return null;
  }
}

// Force the payload's profileId to match the dropdown selection.
function applyOverride() {
  if (!selectedProfile.value) return;
  try {
    const obj = JSON.parse(json.value || "{}") as Record<string, unknown>;
    if (obj.profileId !== selectedProfile.value) {
      obj.profileId = selectedProfile.value;
      json.value = JSON.stringify(obj, null, 2);
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
    const list = props.profiles.profiles.value;
    if (selectedProfile.value || list.length === 0) return;
    const fromPayload = readProfileId(json.value);
    const matchesPayload =
      !!fromPayload && list.some((p) => p.profileId === fromPayload);
    selectedProfile.value = matchesPayload ? fromPayload! : list[0]!.profileId;
    // If the example shipped a profileId we don't recognise, align the payload now.
    if (!matchesPayload) applyOverride();
    props.profiles.loadDetail(selectedProfile.value);
  },
  { immediate: true },
);

function onProfileChange(e: Event) {
  selectedProfile.value = (e.target as HTMLSelectElement).value;
  applyOverride();
  props.profiles.loadDetail(selectedProfile.value);
}

const selectedProfileDetail = computed(() =>
  selectedProfile.value
    ? props.profiles.details.value[selectedProfile.value]
    : null,
);

const showCredentialDataOverrideWarning = computed(() => {
  const example = props.swagger.examples.value[selectedIndex.value];
  if (!example) return false;
  const title = example.title.toLowerCase();
  return (
    title.includes("override") &&
    (title.includes("credentialdata") || title.includes("selective disclosure"))
  );
});

const credentialDataOverrideWarning = computed(() =>
  showCredentialDataOverrideWarning.value
    ? "The override structure may not match your selected profile. Make sure you update the runtimeOverrides object in the request below accordingly."
    : null,
);

function addX5ChainInput() {
  x5ChainValues.value.push("");
}

function removeX5ChainInput(index: number) {
  x5ChainValues.value.splice(index, 1);
  if (x5ChainValues.value.length === 0) x5ChainValues.value.push("");
}

function applySecurityOverridesToJson() {
  optionsError.value = null;

  let payload: Record<string, unknown>;
  try {
    payload = readPayload();
  } catch (e) {
    optionsError.value =
      e instanceof Error ? e.message : "Request body must be valid JSON.";
    return null;
  }

  const runtimeOverrides = (
    payload.runtimeOverrides &&
    typeof payload.runtimeOverrides === "object" &&
    !Array.isArray(payload.runtimeOverrides)
      ? payload.runtimeOverrides
      : {}
  ) as Record<string, unknown>;

  if (keyJson.value.trim()) {
    try {
      runtimeOverrides.issuerKey = parseJwkKey(keyJson.value);
    } catch (e) {
      optionsError.value =
        e instanceof Error ? e.message : "Key must be valid JSON.";
      return null;
    }
  } else {
    delete runtimeOverrides.issuerKey;
  }

  try {
    const x5Chain = parseIssuerPemCertificates(x5ChainValues.value);
    if (x5Chain.length > 0) {
      runtimeOverrides.x5Chain = x5Chain;
    } else {
      delete runtimeOverrides.x5Chain;
    }
  } catch (e) {
    optionsError.value =
      e instanceof Error ? e.message : "Invalid issuer certificate.";
    return null;
  }

  if (Object.keys(runtimeOverrides).length > 0) {
    payload.runtimeOverrides = runtimeOverrides;
  } else {
    delete payload.runtimeOverrides;
  }

  json.value = JSON.stringify(payload, null, 2);
  return payload;
}

watch(
  [keyJson, x5ChainValues],
  () => {
    applySecurityOverridesToJson();
  },
  { deep: true },
);

watch(selectedIndex, () =>
  nextTick(() => {
    applyOverride();
    applySecurityOverridesToJson();
  }),
);

async function submit() {
  const payload = applySecurityOverridesToJson();
  if (!payload) return;

  await props.session.createOffer(payload);
}
</script>

<template>
  <div class="grid gap-4">
    <div class="grid gap-3">
      <div>
        <label class="form-label">Credential Type</label>
        <select
          v-if="profiles.profiles.value.length > 0"
          :value="selectedProfile"
          class="form-select"
          @change="onProfileChange"
        >
          <option
            v-for="p in profiles.profiles.value"
            :key="p.profileId"
            :value="p.profileId"
          >
            {{ p.name ? `${p.name} (${p.profileId})` : p.profileId }}
          </option>
        </select>
        <div
          v-else-if="profiles.loading.value"
          class="form-select text-[--color-text-muted]"
        >
          Loading…
        </div>
        <div
          v-else-if="profiles.error.value"
          class="form-select text-red-500 text-xs"
        >
          {{ profiles.error.value }}
        </div>
        <div v-else class="form-select text-[--color-text-muted]">
          No profiles
        </div>
        <p class="text-xs text-[--color-text-muted] mt-1">
          Overrides the <code>profileId</code> in the payload below.
        </p>
      </div>

      <JsonViewer
        label="Credential Definition"
        :value="selectedProfileDetail"
        :loading="profiles.detailLoading.value"
        :error="profiles.detailError.value"
      />
    </div>

    <JsonEditor
      v-model="json"
      v-model:selected-index="selectedIndex"
      options-label="Credential Offer Options"
      :examples="swagger.examples.value"
      :loading="swagger.loading.value"
      :error="swagger.error.value"
      :warning="credentialDataOverrideWarning"
      @reload="swagger.load()"
    />

    <details class="group rounded-xl border border-[--color-border] bg-white">
      <summary class="cursor-pointer list-none p-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <span class="text-base font-semibold"
              >Offer security overrides</span
            >
            <span class="block text-sm text-[--color-text-muted] mt-1">
              These controls override
              <code>runtimeOverrides.issuerKey</code> and
              <code>runtimeOverrides.x5Chain</code> before creating the offer.
            </span>
          </div>
          <div
            class="inline-flex items-center gap-2 text-sm font-medium text-[--color-text-muted]"
          >
            <span class="group-open:hidden">Expand</span>
            <span class="hidden group-open:inline">Collapse</span>
            <svg
              class="h-4 w-4 transition-transform group-open:rotate-180"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path
                fill-rule="evenodd"
                d="M5.23 7.21a.75.75 0 011.06.02L10 11.17l3.71-3.94a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z"
                clip-rule="evenodd"
              />
            </svg>
          </div>
        </div>
      </summary>

      <div class="grid gap-4 px-4 pb-4">
        <div>
          <label class="form-label">Issuer key (JWK JSON)</label>
          <textarea
            v-model="keyJson"
            class="form-textarea min-h-[180px]"
            spellcheck="false"
            placeholder='{"type":"jwk","jwk":{"kty":"EC","crv":"P-256","x":"...","y":"...","d":"..."}}'
          />
        </div>

        <div class="grid gap-2">
          <label class="form-label">x5Chain certificates (PEM)</label>
          <div
            v-for="(_, index) in x5ChainValues"
            :key="index"
            class="grid gap-2"
          >
            <textarea
              v-model="x5ChainValues[index]"
              class="form-textarea min-h-[120px]"
              spellcheck="false"
              :placeholder="`-----BEGIN CERTIFICATE-----\nCertificate ${index + 1}\n-----END CERTIFICATE-----`"
            />
            <button
              type="button"
              class="btn btn-secondary justify-self-start"
              :disabled="x5ChainValues.length === 1 && !x5ChainValues[0]"
              @click="removeX5ChainInput(index)"
            >
              Remove certificate
            </button>
          </div>
          <button
            type="button"
            class="btn btn-secondary justify-self-start"
            @click="addX5ChainInput"
          >
            Add certificate
          </button>
        </div>

        <p v-if="optionsError" class="text-sm text-red-600">
          {{ optionsError }}
        </p>
      </div>
    </details>

    <div class="flex items-center gap-3">
      <button
        class="btn btn-primary"
        :disabled="submitDisabled"
        @click="submit"
      >
        <svg
          v-if="session.loading.value"
          class="animate-spin h-4 w-4"
          viewBox="0 0 24 24"
          fill="none"
        >
          <circle
            class="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            stroke-width="4"
          />
          <path
            class="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8v8H4z"
          />
        </svg>
        Create Offer
      </button>
      <span v-if="session.error.value" class="text-sm text-red-600">{{
        session.error.value
      }}</span>
    </div>
  </div>
</template>
