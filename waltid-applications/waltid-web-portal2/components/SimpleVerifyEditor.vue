<script setup lang="ts">
import type { useVerifierSession } from "~/composables/useVerifierSession";
import {
  SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS,
  getSimplePidVerificationRequestOption,
} from "~/data/simplePidVerificationRequests";

const props = defineProps<{
  session: ReturnType<typeof useVerifierSession>;
}>();

const selectedOptionId = ref(SIMPLE_CREDENTIAL_OPTIONS[0]!.id);
const selectedPidRequestId = ref(
  SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS[0]!.id,
);
const selectedClaimIds = ref<string[]>([]);

const selectedOption = computed(() =>
  getSimpleCredentialOption(selectedOptionId.value),
);
const isPidOption = computed(() => selectedOption.value.id === "pid");
const selectedPidRequestOption = computed(() =>
  getSimplePidVerificationRequestOption(selectedPidRequestId.value),
);

watch(
  selectedOption,
  (option) => {
    selectedClaimIds.value = option.verifier.claims.map((claim) => claim.id);
  },
  { immediate: true },
);

const selectedClaims = computed(() =>
  selectedOption.value.verifier.claims.filter((claim) =>
    selectedClaimIds.value.includes(claim.id),
  ),
);

const canSubmit = computed(
  () => isPidOption.value || selectedClaims.value.length > 0,
);
const allClaimsSelected = computed(
  () =>
    selectedClaimIds.value.length ===
    selectedOption.value.verifier.claims.length,
);

function selectOption(id: string) {
  selectedOptionId.value = id;
}

function selectAllClaims() {
  selectedClaimIds.value = selectedOption.value.verifier.claims.map(
    (claim) => claim.id,
  );
}

function deselectAllClaims() {
  selectedClaimIds.value = [];
}

function pillClass(tone: string) {
  return {
    "bg-blue-50 text-blue-700 border-blue-200": tone === "blue",
    "bg-green-50 text-green-700 border-green-200": tone === "green",
    "bg-purple-50 text-purple-700 border-purple-200": tone === "purple",
    "bg-slate-50 text-slate-700 border-slate-200": tone === "slate",
  };
}

async function submit() {
  const option = selectedOption.value;
  if (isPidOption.value) {
    await props.session.createSession(
      JSON.parse(JSON.stringify(selectedPidRequestOption.value.requestBody)),
    );
    return;
  }

  const credential: Record<string, unknown> = {
    id: option.verifier.credentialId,
    format: option.format,
    meta: option.verifier.meta,
    claims: selectedClaims.value.map((claim) => ({
      id: claim.id,
      path: claim.path,
    })),
  };

  await props.session.createSession({
    flow_type: "cross_device",
    core_flow: {
      dcql_query: {
        credentials: [credential],
      },
    },
  });
}
</script>

<template>
  <div class="grid gap-5">
    <section>
      <h2 class="text-lg font-semibold mb-1">Choose what to verify</h2>
      <p class="text-sm text-[--color-text-muted] mb-3">
        Pick a credential type and choose which information the wallet should
        present.
      </p>

      <div class="grid md:grid-cols-3 gap-3">
        <button
          v-for="option in SIMPLE_CREDENTIAL_OPTIONS"
          :key="option.id"
          type="button"
          class="text-left rounded-xl border p-4 transition-colors bg-white"
          :class="
            selectedOptionId === option.id
              ? 'border-slate-900 ring-2 ring-slate-900/10'
              : 'border-[--color-border] hover:border-[--color-border-strong]'
          "
          @click="selectOption(option.id)"
        >
          <div class="flex items-start justify-between gap-3">
            <div>
              <h3 class="font-semibold text-base">{{ option.title }}</h3>
              <p class="text-xs text-[--color-text-muted] mt-1">
                {{ option.format }}
              </p>
            </div>
          </div>

          <p class="text-sm text-[--color-text-secondary] mt-3">
            {{ option.description }}
          </p>

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

    <section
      v-if="isPidOption"
      class="rounded-xl border border-[--color-border] bg-white p-4"
    >
      <label class="form-label">PID wallet profile</label>
      <select v-model="selectedPidRequestId" class="form-select">
        <option
          v-for="requestOption in SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS"
          :key="requestOption.id"
          :value="requestOption.id"
        >
          {{ requestOption.label }}
        </option>
      </select>
      <p class="mt-2 text-xs text-[--color-text-muted]">
        {{ selectedPidRequestOption.description }}
      </p>
    </section>

    <details
      v-else
      open
      class="group rounded-xl border border-[--color-border] bg-white"
    >
      <summary class="cursor-pointer list-none p-4">
        <div class="flex items-start justify-between gap-3">
          <div>
            <label class="form-label !mb-0">Claims to request</label>
            <p class="mt-1 text-xs text-[--color-text-muted]">
              The verifier will create an unsigned, unencrypted OpenID4VP
              request over HTTP for the selected claims.
            </p>
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

      <div class="px-4 pb-4">
        <div class="mb-3 flex justify-end gap-2">
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
      </div>
    </details>

    <div class="flex items-center gap-3">
      <button
        class="btn btn-primary"
        :disabled="!canSubmit || session.loading.value"
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
        Create Verification Session
      </button>
      <span v-if="session.error.value" class="text-sm text-red-600">{{
        session.error.value
      }}</span>
    </div>
  </div>
</template>
