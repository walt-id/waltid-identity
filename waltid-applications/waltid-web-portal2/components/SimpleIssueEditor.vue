<script setup lang="ts">
import type { useIssuerSession } from "~/composables/useIssuerSession";
import { getDcApiIssuanceSupport } from "~/utils/dcApiIssuance";

type AuthMethod = "AUTHORIZED" | "PRE_AUTHORIZED";
type DeliveryMethod = "qr" | "dc_api";

const props = defineProps<{
  session: ReturnType<typeof useIssuerSession>;
}>();

const selectedOptionId = ref(SIMPLE_CREDENTIAL_OPTIONS[0]!.id);
const authMethod = ref<AuthMethod>("PRE_AUTHORIZED");
const deliveryMethod = ref<DeliveryMethod>("qr");
const credentialDataJson = ref("");
const parseError = ref<string | null>(null);
const dcApiSupport = ref(getDcApiIssuanceSupport());

const issueCredentialOptions = computed(() =>
  SIMPLE_CREDENTIAL_OPTIONS.filter((option) => !option.verifyOnly),
);
const selectedOption = computed(() =>
  getSimpleCredentialOption(selectedOptionId.value),
);

onMounted(() => {
  dcApiSupport.value = getDcApiIssuanceSupport();
});

watch(deliveryMethod, (method) => {
  if (method === "dc_api") {
    dcApiSupport.value = getDcApiIssuanceSupport();
  }
});

watch(
  selectedOption,
  (option) => {
    credentialDataJson.value = JSON.stringify(
      option.defaultCredentialData,
      null,
      2,
    );
    parseError.value = null;
  },
  { immediate: true },
);

const canSubmit = computed(() => {
  if (!credentialDataJson.value.trim()) return true;
  try {
    JSON.parse(credentialDataJson.value);
    return true;
  } catch {
    return false;
  }
});

function selectOption(id: string) {
  selectedOptionId.value = id;
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
  parseError.value = null;

  let credentialData: Record<string, unknown> | null = null;
  if (credentialDataJson.value.trim()) {
    try {
      const parsed = JSON.parse(credentialDataJson.value);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("Credential data must be a JSON object.");
      }
      credentialData = parsed as Record<string, unknown>;
    } catch (e) {
      parseError.value =
        e instanceof Error ? e.message : "Credential data must be valid JSON.";
      return;
    }
  }

  const payload: Record<string, unknown> = {
    profileId: selectedOption.value.profileId,
    authMethod: authMethod.value,
  };

  if (credentialData && Object.keys(credentialData).length > 0) {
    payload.runtimeOverrides = { credentialData };
  }

  if (deliveryMethod.value === "dc_api") {
    await props.session.createDcApiOffer(payload);
  } else {
    await props.session.createOffer(payload);
  }
}
</script>

<template>
  <div class="grid gap-5">
    <section>
      <h2 class="text-lg font-semibold mb-1">Choose what to issue</h2>
      <p class="text-sm text-[--color-text-muted] mb-3">
        Pick a credential type and customise only the data that should appear in
        the credential.
      </p>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        <button
          v-for="option in issueCredentialOptions"
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
                {{ option.profileId }}
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
      <p class="text-xs text-[--color-text-muted] mt-2">
        Pre-authorized is simplest for demos. Authorized uses the wallet
        authorization flow.
      </p>
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
      <p class="text-xs text-[--color-text-muted] mt-2">
        QR shows an OpenID4VCI offer for wallets to scan. DC API starts wallet
        engagement via <code>navigator.credentials.create</code>; the result log
        follows issuer OpenID4VCI events (Chrome origin trial).
      </p>
      <p
        v-if="deliveryMethod === 'dc_api' && !dcApiSupport.supported"
        class="text-xs text-amber-700 mt-2 rounded-lg border border-amber-200 bg-amber-50 p-2"
      >
        {{ dcApiSupport.reason }}
        You can still create the offer; the browser call will fail until the
        prerequisites are met. QR delivery remains available.
      </p>
    </section>

    <section>
      <label class="form-label">Credential data override</label>
      <p class="text-xs text-[--color-text-muted] mb-2">
        Edit the data below. It will be applied as
        <code>runtimeOverrides.credentialData</code> for the selected profile.
      </p>
      <textarea
        v-model="credentialDataJson"
        class="form-textarea"
        spellcheck="false"
      />
      <p v-if="parseError" class="text-sm text-red-600 mt-2">
        {{ parseError }}
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
        Create Offer
      </button>
      <span v-if="session.error.value" class="text-sm text-red-600">{{
        session.error.value
      }}</span>
    </div>
  </div>
</template>
