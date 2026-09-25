<script setup lang="ts">
import type { useVerifierSession } from "~/composables/useVerifierSession";
import type { useProfiles } from "~/composables/useProfiles";
import type {
  IssuerCredentialConfiguration,
  IssuerCredentialDisplay,
} from "~/composables/useIssuerMetadata";
import {
  SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS,
  getSimplePidVerificationRequestOption,
} from "~/data/simplePidVerificationRequests";
import {
  getDcApiPresentationSupport,
  type DcApiPresentationSupport,
} from "~/utils/dcApiPresentation";
import {
  annexCRequestedElements,
  claimsFromIssuerMetadata,
  dcqlFromIssuerMetadata,
  isMdocConfiguration,
  isPidConfiguration,
} from "~/utils/dcqlFromIssuerMetadata";

type DeliveryMethod = "qr" | "dc_api";
type DcApiProtocol = "openid4vp" | "iso18013_7";

const props = defineProps<{
  session: ReturnType<typeof useVerifierSession>;
  profiles: ReturnType<typeof useProfiles>;
}>();

const config = useRuntimeConfig();
const issuerBase = config.public.issuerBase as string;
const issuerMetadata = useIssuerMetadata(issuerBase);

const selectedProfileId = ref<string | null>(null);
const selectedPidRequestId = ref(
  SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS[0]!.id,
);
const selectedClaimIds = ref<string[]>([]);
const deliveryMethod = ref<DeliveryMethod>("qr");
const dcApiProtocol = ref<DcApiProtocol>("openid4vp");
const dcApiSupport = ref<DcApiPresentationSupport>(
  getDcApiPresentationSupport(),
);

onMounted(() => {
  dcApiSupport.value = getDcApiPresentationSupport();
  issuerMetadata.load();
});

watch(deliveryMethod, (method) => {
  if (method === "dc_api") {
    dcApiSupport.value = getDcApiPresentationSupport();
  }
});

interface ProfileCard {
  profileId: string;
  name: string;
  credentialConfigurationId: string;
  description?: string;
  backgroundImageUri?: string;
  backgroundColor: string;
  configuration?: IssuerCredentialConfiguration;
}

function firstDisplay(
  configuration?: IssuerCredentialConfiguration,
): IssuerCredentialDisplay | undefined {
  return configuration?.credential_metadata?.display?.[0];
}

const profileCards = computed<ProfileCard[]>(() =>
  props.profiles.profiles.value.map((profile) => {
    const credentialConfigurationId = profile.credentialConfigurationId ?? "";
    const configuration = issuerMetadata.configurationFor(
      credentialConfigurationId,
    );
    const display = firstDisplay(configuration);
    return {
      profileId: profile.profileId,
      name: display?.name || profile.name || profile.profileId,
      credentialConfigurationId,
      description: display?.description,
      backgroundImageUri: display?.background_image?.uri,
      backgroundColor: display?.background_color || "#0f172a",
      configuration,
    };
  }),
);

const selectedCard = computed(
  () =>
    profileCards.value.find(
      (card) => card.profileId === selectedProfileId.value,
    ) ?? null,
);

const availableClaims = computed(() =>
  claimsFromIssuerMetadata(selectedCard.value?.configuration),
);

const selectedClaims = computed(() =>
  availableClaims.value.filter((claim) =>
    selectedClaimIds.value.includes(claim.id),
  ),
);

const isPidCard = computed(() =>
  isPidConfiguration(
    selectedCard.value?.configuration,
    selectedCard.value?.credentialConfigurationId,
  ),
);

const isMdocCard = computed(() =>
  isMdocConfiguration(selectedCard.value?.configuration),
);

const showPidWalletProfile = computed(
  () => isPidCard.value && deliveryMethod.value === "qr",
);

const showIso180137 = computed(
  () => deliveryMethod.value === "dc_api" && isMdocCard.value,
);

const selectedPidRequestOption = computed(() =>
  getSimplePidVerificationRequestOption(selectedPidRequestId.value),
);

const allClaimsSelected = computed(
  () =>
    availableClaims.value.length > 0 &&
    selectedClaimIds.value.length === availableClaims.value.length,
);

const canSubmit = computed(() => {
  if (!selectedCard.value?.configuration) return false;
  if (selectedClaims.value.length === 0) return false;
  if (deliveryMethod.value === "dc_api" && !dcApiSupport.value.supported) {
    return false;
  }
  return true;
});

watch(
  () => selectedCard.value?.credentialConfigurationId,
  () => {
    selectedClaimIds.value = claimsFromIssuerMetadata(
      selectedCard.value?.configuration,
    ).map((claim) => claim.id);
  },
);

function selectCard(profileId: string) {
  selectedProfileId.value = profileId;
}

function clearSelection() {
  selectedProfileId.value = null;
}

function selectAllClaims() {
  selectedClaimIds.value = availableClaims.value.map((claim) => claim.id);
}

function deselectAllClaims() {
  selectedClaimIds.value = [];
}

function buildCredentialQuery() {
  const card = selectedCard.value;
  if (!card?.configuration) {
    throw new Error("No credential selected");
  }
  return dcqlFromIssuerMetadata(
    card.credentialConfigurationId,
    card.configuration,
    selectedClaims.value,
  );
}

function withGeneratedPidDcql() {
  const option = selectedPidRequestOption.value;
  const body = structuredClone(option.requestBody) as Record<string, unknown>;
  const coreFlow =
    body.core_flow && typeof body.core_flow === "object"
      ? { ...(body.core_flow as Record<string, unknown>) }
      : {};
  coreFlow.dcql_query = { credentials: [buildCredentialQuery()] };
  body.core_flow = coreFlow;
  return { materialId: option.materialId, body };
}

async function submit() {
  if (!canSubmit.value) return;

  if (deliveryMethod.value === "dc_api") {
    if (showIso180137.value && dcApiProtocol.value === "iso18013_7") {
      await props.session.createDcApiSession(
        {
          flow_type: "dc_api_18013_7",
          core_flow: {
            requestedElements: annexCRequestedElements(
              selectedCard.value!.configuration!,
              selectedClaims.value,
            ),
          },
        },
        true,
      );
      return;
    }

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
    return;
  }

  if (showPidWalletProfile.value) {
    const pid = withGeneratedPidDcql();
    await props.session.createPidSession(pid.materialId, pid.body);
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
</script>

<template>
  <div class="grid gap-5">
    <div
      v-if="profiles.loading.value || issuerMetadata.loading.value"
      class="text-sm text-[--color-text-muted]"
    >
      Loading credential profiles…
    </div>
    <div
      v-else-if="profiles.error.value || issuerMetadata.error.value"
      class="text-sm text-red-600"
    >
      {{ profiles.error.value || issuerMetadata.error.value }}
    </div>

    <template v-else-if="!selectedCard">
      <section>
        <h2 class="text-lg font-semibold mb-1">Choose what to verify</h2>
        <p class="text-sm text-[--color-text-muted] mb-3">
          Select a credential. Verification options appear after the card
          enlarges.
        </p>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <button
            v-for="card in profileCards"
            :key="card.profileId"
            type="button"
            class="group relative w-full overflow-hidden rounded-2xl shadow-sm ring-1 ring-black/5 transition duration-150 hover:-translate-y-0.5 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-900"
            :style="{ backgroundColor: card.backgroundColor }"
            :aria-label="card.name"
            @click="selectCard(card.profileId)"
          >
            <span class="block w-full" style="aspect-ratio: 1.586" />
            <img
              v-if="card.backgroundImageUri"
              :src="card.backgroundImageUri"
              :alt="card.name"
              class="absolute inset-0 h-full w-full object-cover"
            />
            <span
              v-else
              class="absolute inset-0 flex items-end p-4 text-white font-semibold"
            >
              {{ card.name }}
            </span>
          </button>
        </div>
      </section>
    </template>

    <template v-else>
      <div>
        <button
          type="button"
          class="text-sm font-medium text-[--color-text-muted] hover:text-[--color-text] mb-3"
          @click="clearSelection"
        >
          Back to profiles
        </button>

        <div
          class="relative w-full overflow-hidden rounded-2xl shadow-sm ring-1 ring-black/5"
          :style="{ backgroundColor: selectedCard.backgroundColor }"
        >
          <span class="block w-full" style="aspect-ratio: 1.586" />
          <img
            v-if="selectedCard.backgroundImageUri"
            :src="selectedCard.backgroundImageUri"
            :alt="selectedCard.name"
            class="absolute inset-0 h-full w-full object-cover"
          />
        </div>

        <div class="mt-4">
          <h2 class="text-lg font-semibold">{{ selectedCard.name }}</h2>
          <p
            v-if="selectedCard.description"
            class="text-sm text-[--color-text-muted] mt-1"
          >
            {{ selectedCard.description }}
          </p>
        </div>
      </div>

      <details class="group rounded-xl border border-[--color-border] bg-white">
        <summary class="cursor-pointer list-none p-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <label class="form-label !mb-0">Claims to request</label>
              <p class="mt-1 text-xs text-[--color-text-muted]">
                Claims come from this credential’s issuer metadata.
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

          <p
            v-if="availableClaims.length === 0"
            class="text-sm text-amber-800 rounded-lg border border-amber-200 bg-amber-50 p-3"
          >
            This credential has no advertised claims in issuer metadata.
          </p>
          <div v-else class="grid sm:grid-cols-2 gap-2">
            <label
              v-for="claim in availableClaims"
              :key="claim.id"
              class="flex items-start gap-3 rounded-lg border border-[--color-border] bg-white p-3 cursor-pointer hover:border-[--color-border-strong]"
            >
              <input
                v-model="selectedClaimIds"
                type="checkbox"
                :value="claim.id"
                class="mt-1"
              />
              <span class="block text-sm font-medium">{{ claim.label }}</span>
            </label>
          </div>
        </div>
      </details>

      <section>
        <label class="form-label">Delivery method</label>
        <div
          class="inline-flex rounded-lg border border-[--color-border-strong] bg-white p-1"
        >
          <button
            type="button"
            class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
            :class="
              deliveryMethod === 'qr'
                ? 'bg-slate-900 text-white'
                : 'text-[--color-text-muted] hover:text-[--color-text]'
            "
            @click="deliveryMethod = 'qr'"
          >
            QR / deep link
          </button>
          <button
            type="button"
            class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
            :class="
              deliveryMethod === 'dc_api'
                ? 'bg-slate-900 text-white'
                : 'text-[--color-text-muted] hover:text-[--color-text]'
            "
            @click="deliveryMethod = 'dc_api'"
          >
            Digital Credentials API
          </button>
        </div>
        <div
          v-if="deliveryMethod === 'dc_api' && !dcApiSupport.supported"
          class="text-xs text-amber-800 mt-2 rounded-lg border border-amber-200 bg-amber-50 p-3 space-y-2"
        >
          <p class="font-medium">
            Digital Credentials API presentation is not available in this
            browser.
          </p>
          <p>{{ dcApiSupport.reason }}</p>
          <p>
            Switch to <strong>QR / deep link</strong> to create a verification
            session.
          </p>
        </div>
      </section>

      <section v-if="showIso180137">
        <label class="form-label">DC API protocol</label>
        <div
          class="inline-flex rounded-lg border border-[--color-border-strong] bg-white p-1"
        >
          <button
            type="button"
            class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
            :class="
              dcApiProtocol === 'openid4vp'
                ? 'bg-slate-900 text-white'
                : 'text-[--color-text-muted] hover:text-[--color-text]'
            "
            @click="dcApiProtocol = 'openid4vp'"
          >
            OpenID4VP
          </button>
          <button
            type="button"
            class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
            :class="
              dcApiProtocol === 'iso18013_7'
                ? 'bg-slate-900 text-white'
                : 'text-[--color-text-muted] hover:text-[--color-text]'
            "
            @click="dcApiProtocol = 'iso18013_7'"
          >
            ISO 18013-7
          </button>
        </div>
        <p class="mt-2 text-xs text-[--color-text-muted]">
          Use ISO 18013-7 for iOS / native wallet compatibility. Session
          <code>expectedOrigins</code> is set to this page’s origin.
        </p>
      </section>

      <section v-if="showPidWalletProfile">
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

      <div class="flex flex-col sm:flex-row sm:items-center gap-3">
        <button
          class="btn btn-primary w-full sm:w-auto"
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
    </template>
  </div>
</template>
