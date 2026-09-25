<script setup lang="ts">
import type { useIssuerSession } from "~/composables/useIssuerSession";
import type { useProfiles } from "~/composables/useProfiles";
import {
  DC_API_ISSUANCE_DOCS_URL,
  getDcApiIssuanceSupport,
} from "~/utils/dcApiIssuance";
import { buildProfileCards, type ProfileCard } from "~/utils/profileCards";

type AuthMethod = "AUTHORIZED" | "PRE_AUTHORIZED";
type DeliveryMethod = "qr" | "dc_api";

const props = defineProps<{
  session: ReturnType<typeof useIssuerSession>;
  profiles: ReturnType<typeof useProfiles>;
  profileId?: string | null;
  card?: ProfileCard | null;
  embedded?: boolean;
}>();

const config = useRuntimeConfig();
const issuerBase = config.public.issuerBase as string;
const issuerMetadata = useIssuerMetadata(issuerBase);

const selectedProfileId = ref<string | null>(
  props.card?.profileId ?? props.profileId ?? null,
);
const authMethod = ref<AuthMethod>("PRE_AUTHORIZED");
const deliveryMethod = ref<DeliveryMethod>("qr");
const dcApiSupport = ref(getDcApiIssuanceSupport());

onMounted(() => {
  dcApiSupport.value = getDcApiIssuanceSupport();
  if (!props.embedded) issuerMetadata.load();
});

watch(deliveryMethod, (method) => {
  if (method === "dc_api") {
    dcApiSupport.value = getDcApiIssuanceSupport();
  }
});

watch(
  () => props.card?.profileId ?? props.profileId,
  (profileId) => {
    if (profileId) selectedProfileId.value = profileId;
  },
);

watch(
  selectedProfileId,
  (profileId) => {
    if (profileId) {
      props.profiles.loadDetail(profileId);
    }
  },
  { immediate: true },
);

const profileCards = computed(() =>
  buildProfileCards(
    props.profiles.profiles.value,
    issuerMetadata.configurationFor,
  ),
);

const selectedCard = computed(
  () =>
    props.card ??
    profileCards.value.find(
      (card) => card.profileId === selectedProfileId.value,
    ) ??
    null,
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
      v-if="
        !embedded &&
        (profiles.loading.value || issuerMetadata.loading.value)
      "
      class="text-sm text-[--color-text-muted]"
    >
      Loading credential profiles…
    </div>
    <div
      v-else-if="
        !embedded && (profiles.error.value || issuerMetadata.error.value)
      "
      class="text-sm text-red-600"
    >
      {{ profiles.error.value || issuerMetadata.error.value }}
    </div>

    <SimpleCredentialGrid
      v-else-if="!embedded && !selectedCard"
      :cards="profileCards"
      @select="selectCard"
    />

    <template v-else-if="selectedCard">
      <template v-if="!embedded">
        <div>
          <button
            type="button"
            class="text-sm font-medium text-[--color-text-muted] hover:text-[--color-text] mb-3"
            @click="clearSelection"
          >
            Back to credentials
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
      </template>

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
