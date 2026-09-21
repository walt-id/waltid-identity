<script setup lang="ts">
import type { useIssuerSession } from "~/composables/useIssuerSession";
import type { useProfiles } from "~/composables/useProfiles";
import type {
  IssuerCredentialConfiguration,
  IssuerCredentialDisplay,
} from "~/composables/useIssuerMetadata";
import {
  DC_API_ISSUANCE_DOCS_URL,
  getDcApiIssuanceSupport,
} from "~/utils/dcApiIssuance";

type AuthMethod = "AUTHORIZED" | "PRE_AUTHORIZED";
type DeliveryMethod = "qr" | "dc_api";

const props = defineProps<{
  session: ReturnType<typeof useIssuerSession>;
  profiles: ReturnType<typeof useProfiles>;
}>();

const config = useRuntimeConfig();
const issuerBase = config.public.issuerBase as string;
const issuerMetadata = useIssuerMetadata(issuerBase);

const selectedProfileId = ref<string | null>(null);
const authMethod = ref<AuthMethod>("PRE_AUTHORIZED");
const deliveryMethod = ref<DeliveryMethod>("qr");
const dcApiSupport = ref(getDcApiIssuanceSupport());

onMounted(() => {
  dcApiSupport.value = getDcApiIssuanceSupport();
  issuerMetadata.load();
});

watch(deliveryMethod, (method) => {
  if (method === "dc_api") {
    dcApiSupport.value = getDcApiIssuanceSupport();
  }
});

watch(selectedProfileId, (profileId) => {
  if (profileId) {
    props.profiles.loadDetail(profileId);
  }
});

interface ProfileCard {
  profileId: string;
  name: string;
  credentialConfigurationId: string;
  format: string;
  formatLabel: string;
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

function formatLabel(format?: string): string {
  if (format === "jwt_vc_json") return "W3C VC";
  if (format === "dc+sd-jwt") return "dc+sd-jwt";
  if (format === "mso_mdoc") return "mso_mdoc";
  return format || "credential";
}

function formatTone(format?: string): string {
  if (format === "jwt_vc_json") return "blue";
  if (format === "dc+sd-jwt") return "green";
  if (format === "mso_mdoc") return "purple";
  return "slate";
}

function pillClass(tone: string) {
  return {
    "bg-blue-50 text-blue-700 border-blue-200": tone === "blue",
    "bg-green-50 text-green-700 border-green-200": tone === "green",
    "bg-purple-50 text-purple-700 border-purple-200": tone === "purple",
    "bg-slate-50 text-slate-700 border-slate-200": tone === "slate",
  };
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
      format: configuration?.format || "",
      formatLabel: formatLabel(configuration?.format),
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

const selectedProfileDetail = computed(() =>
  selectedProfileId.value
    ? props.profiles.details.value[selectedProfileId.value]
    : null,
);

const canSubmit = computed(() => {
  if (!selectedCard.value) return false;
  if (deliveryMethod.value === "dc_api" && !dcApiSupport.value.supported) {
    return false;
  }
  return true;
});

function selectCard(profileId: string) {
  selectedProfileId.value = profileId;
}

function clearSelection() {
  selectedProfileId.value = null;
}

async function submit() {
  if (!selectedCard.value) return;
  const payload = {
    profileId: selectedCard.value.profileId,
    authMethod: authMethod.value,
  };
  if (deliveryMethod.value === "dc_api") {
    await props.session.createDcApiOffer(payload);
  } else {
    await props.session.createOffer(payload);
  }
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
        <h2 class="text-lg font-semibold mb-1">Choose what to issue</h2>
        <p class="text-sm text-[--color-text-muted] mb-3">
          Select a credential profile. Issuance options appear after the card
          enlarges.
        </p>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <button
            v-for="card in profileCards"
            :key="card.profileId"
            type="button"
            class="text-left rounded-xl border border-[--color-border] bg-white p-3 transition-colors hover:border-[--color-border-strong]"
            @click="selectCard(card.profileId)"
          >
            <div
              class="relative w-full overflow-hidden rounded-lg"
              :style="{ backgroundColor: card.backgroundColor }"
              style="aspect-ratio: 1.586"
            >
              <img
                v-if="card.backgroundImageUri"
                :src="card.backgroundImageUri"
                :alt="card.name"
                class="absolute inset-0 h-full w-full object-cover"
              />
              <div
                v-else
                class="absolute inset-0 flex items-end p-3 text-white font-semibold"
              >
                {{ card.name }}
              </div>
            </div>
            <div class="mt-3 flex items-start justify-between gap-2">
              <h3 class="font-semibold text-sm leading-snug">{{ card.name }}</h3>
              <span
                class="inline-flex shrink-0 rounded-full border px-2 py-0.5 text-[11px] font-medium"
                :class="pillClass(formatTone(card.format))"
              >
                {{ card.formatLabel }}
              </span>
            </div>
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
          class="relative w-full overflow-hidden rounded-xl border border-[--color-border]"
          :style="{ backgroundColor: selectedCard.backgroundColor }"
          style="aspect-ratio: 1.586"
        >
          <img
            v-if="selectedCard.backgroundImageUri"
            :src="selectedCard.backgroundImageUri"
            :alt="selectedCard.name"
            class="absolute inset-0 h-full w-full object-cover"
          />
        </div>

        <div class="mt-3 flex items-center justify-between gap-3">
          <div>
            <h2 class="text-lg font-semibold">{{ selectedCard.name }}</h2>
            <p
              v-if="selectedCard.description"
              class="text-sm text-[--color-text-muted] mt-1"
            >
              {{ selectedCard.description }}
            </p>
          </div>
          <span
            class="inline-flex shrink-0 rounded-full border px-2.5 py-1 text-xs font-medium"
            :class="pillClass(formatTone(selectedCard.format))"
          >
            {{ selectedCard.formatLabel }}
          </span>
        </div>
      </div>

      <JsonViewer
        label="Credential definition"
        :value="selectedCard.configuration"
        :loading="issuerMetadata.loading.value"
        :error="issuerMetadata.error.value"
      />
      <JsonViewer
        label="Profile"
        :value="selectedProfileDetail"
        :loading="profiles.detailLoading.value"
        :error="profiles.detailError.value"
      />

      <section>
        <label class="form-label">Issuance flow</label>
        <div
          class="inline-flex rounded-lg border border-[--color-border-strong] bg-white p-1"
        >
          <button
            type="button"
            class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
            :class="
              authMethod === 'PRE_AUTHORIZED'
                ? 'bg-slate-900 text-white'
                : 'text-[--color-text-muted] hover:text-[--color-text]'
            "
            @click="authMethod = 'PRE_AUTHORIZED'"
          >
            Pre-authorized
          </button>
          <button
            type="button"
            class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
            :class="
              authMethod === 'AUTHORIZED'
                ? 'bg-slate-900 text-white'
                : 'text-[--color-text-muted] hover:text-[--color-text]'
            "
            @click="authMethod = 'AUTHORIZED'"
          >
            Authorized
          </button>
        </div>
      </section>

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
            Digital Credentials API issuance is not available in this browser.
          </p>
          <p>{{ dcApiSupport.reason }}</p>
          <p>
            Switch to <strong>QR / deep link</strong>, or follow the
            <a
              :href="DC_API_ISSUANCE_DOCS_URL"
              target="_blank"
              rel="noopener noreferrer"
              class="underline font-medium text-amber-950"
            >
              Chrome Digital Credentials API issuance docs
            </a>
            .
          </p>
        </div>
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
          Create Offer
        </button>
        <span v-if="session.error.value" class="text-sm text-red-600">{{
          session.error.value
        }}</span>
      </div>
    </template>
  </div>
</template>
