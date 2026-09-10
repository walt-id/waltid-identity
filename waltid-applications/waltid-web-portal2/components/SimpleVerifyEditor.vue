<script setup lang="ts">
import type { useVerifierSession } from "~/composables/useVerifierSession";
import {
  SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS,
  getSimplePidVerificationRequestOption,
} from "~/data/simplePidVerificationRequests";
import {
  getDcApiPresentationSupport,
  type DcApiPresentationSupport,
} from "~/utils/dcApiPresentation";

const props = defineProps<{
  session: ReturnType<typeof useVerifierSession>;
}>();

const selectedOptionId = ref(SIMPLE_CREDENTIAL_OPTIONS[0]!.id);
const selectedPidRequestId = ref(
  SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS[0]!.id,
);
const selectedClaimIds = ref<string[]>([]);
const dcApiSupport = ref<DcApiPresentationSupport>(
  getDcApiPresentationSupport(),
);

const selectedOption = computed(() =>
  getSimpleCredentialOption(selectedOptionId.value),
);
const isPidOption = computed(() => selectedOption.value.id === "pid");
const isDcApiOption = computed(
  () => selectedOption.value.verifierFlowType === "dc_api",
);
const selectedPidRequestOption = computed(() =>
  getSimplePidVerificationRequestOption(selectedPidRequestId.value),
);

watch(
  selectedOption,
  (option) => {
    selectedClaimIds.value = option.verifier.claims.map((claim) => claim.id);
    if (option.verifierFlowType === "dc_api") {
      dcApiSupport.value = getDcApiPresentationSupport();
    }
  },
  { immediate: true },
);

const selectedClaims = computed(() =>
  selectedOption.value.verifier.claims.filter((claim) =>
    selectedClaimIds.value.includes(claim.id),
  ),
);

const canSubmit = computed(
  () =>
    (isPidOption.value || selectedClaims.value.length > 0) &&
    (!isDcApiOption.value || dcApiSupport.value.supported),
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

function buildCredentialQuery() {
  const option = selectedOption.value;
  return {
    id: option.verifier.credentialId,
    format: option.format,
    meta: option.verifier.meta,
    claims: selectedClaims.value.map((claim) => ({
      id: claim.id,
      path: claim.path,
    })),
  };
}

function buildAnnexCRequestedElements() {
  const option = selectedOption.value;
  const doctype =
    typeof option.verifier.meta.doctype_value === "string"
      ? option.verifier.meta.doctype_value
      : "org.iso.18013.5.1.mDL";

  const namespaceToElements: Record<string, string[]> = {};
  for (const claim of selectedClaims.value) {
    if (claim.path.length < 2) continue;
    const namespace = claim.path[0]!;
    const elementId = claim.path[1]!;
    const elements = namespaceToElements[namespace] ?? [];
    if (!elements.includes(elementId)) elements.push(elementId);
    namespaceToElements[namespace] = elements;
  }

  return {
    [doctype]: namespaceToElements,
  };
}

async function submitCrossDevice() {
  if (isPidOption.value) {
    const option = selectedPidRequestOption.value;
    await props.session.createPidSession(
      option.materialId,
      JSON.parse(JSON.stringify(option.requestBody)),
    );
    return;
  }

  await props.session.createSession({
    flow_type: "cross_device",
    core_flow: {
      dcql_query: {
        credentials: [buildCredentialQuery()],
      },
    },
  });
}

async function submitOpenId4VpDcApi() {
  await props.session.createDcApiSession(
    {
      flow_type: "dc_api_openid4vp",
      haip: false,
      core_flow: {
        dcql_query: {
          credentials: [buildCredentialQuery()],
        },
      },
    },
    false,
  );
}

async function submitIso180137DcApi() {
  await props.session.createDcApiSession(
    {
      flow_type: "dc_api_18013_7",
      core_flow: {
        requestedElements: buildAnnexCRequestedElements(),
      },
    },
    true,
  );
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

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
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
        <a
          v-if="selectedPidRequestOption.link"
          :href="selectedPidRequestOption.link"
          target="_blank"
          class="text-blue-500"
          >Learn more</a
        >
      </p>
    </section>

    <template v-else>
      <details
        class="group rounded-xl border border-[--color-border] bg-white"
      >
        <summary class="cursor-pointer list-none p-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <label class="form-label !mb-0">Claims to request</label>
              <p class="mt-1 text-xs text-[--color-text-muted]">
                <template v-if="isDcApiOption">
                  Selected claims are requested through the Digital Credentials
                  API using either OpenID4VP or ISO 18013-7.
                </template>
                <template v-else>
                  The verifier will create an unsigned, unencrypted OpenID4VP
                  request over HTTP for the selected claims.
                </template>
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

      <section
        v-if="isDcApiOption"
        class="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900"
      >
        <p class="font-medium">Digital Credentials API</p>
        <p class="mt-1 text-xs text-blue-800/90">
          Requires a browser that supports the Digital Credentials API
          (<code>window.DigitalCredential</code>) on an HTTPS origin. The wallet
          picker opens on this device — no QR code is shown. Use ISO 18013-7 for
          iOS / native wallet compatibility. Session
          <code>expectedOrigins</code> is set to this page's origin.
        </p>
        <div
          v-if="!dcApiSupport.supported"
          class="mt-3 rounded-md border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900"
        >
          <p class="font-medium">
            Digital Credentials API presentation is not available in this
            browser.
          </p>
          <p class="mt-1">{{ dcApiSupport.reason }}</p>
        </div>
      </section>
    </template>

    <div class="flex flex-col gap-3">
      <template v-if="isDcApiOption">
        <div class="flex flex-col sm:flex-row gap-3">
          <button
            class="btn btn-primary w-full sm:w-auto"
            :disabled="!canSubmit || session.loading.value"
            @click="submitOpenId4VpDcApi"
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
            Verify with DC API (OpenID4VP)
          </button>
          <button
            class="btn btn-primary w-full sm:w-auto"
            :disabled="!canSubmit || session.loading.value"
            @click="submitIso180137DcApi"
          >
            Verify with DC API (ISO 18013-7)
          </button>
        </div>
      </template>
      <template v-else>
        <button
          class="btn btn-primary w-full sm:w-auto"
          :disabled="!canSubmit || session.loading.value"
          @click="submitCrossDevice"
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
      </template>
      <span v-if="session.error.value" class="text-sm text-red-600">{{
        session.error.value
      }}</span>
    </div>
  </div>
</template>
