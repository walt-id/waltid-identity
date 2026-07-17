<script setup lang="ts">
import type { useSwaggerExamples } from "~/composables/useSwaggerExamples";
import type { useVerifierSession } from "~/composables/useVerifierSession";

const props = defineProps<{
  swagger: ReturnType<typeof useSwaggerExamples>;
  session: ReturnType<typeof useVerifierSession>;
}>();

const json = defineModel<string>("json", { required: true });
const selectedIndex = defineModel<number>("selectedIndex", { default: 0 });

const config = useRuntimeConfig();

type ClientIdType =
  | "x509_hash"
  | "x509_san_dns"
  | "redirect_uri"
  | "decentralized_identifier"
  | "verifier_attestation"
  | "pre_registered";

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function runtimeConfigValueToString(value: unknown): string {
  if (value == null) return "";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean")
    return String(value);
  return JSON.stringify(value);
}

function wrapRawJwkPreset(value: unknown): unknown {
  if (
    isRecord(value) &&
    value.type !== "jwk" &&
    typeof value.kty === "string"
  ) {
    return { type: "jwk", jwk: value };
  }

  return value;
}

function formatJsonPreset(value: unknown): string {
  const rawValue = runtimeConfigValueToString(value);
  if (!rawValue.trim()) return "";

  try {
    return JSON.stringify(wrapRawJwkPreset(JSON.parse(rawValue)), null, 2);
  } catch {
    return rawValue;
  }
}

function parseX5cPreset(value: unknown): string[] {
  const trimmed = runtimeConfigValueToString(value).trim();
  if (!trimmed) return [""];

  try {
    const parsed = JSON.parse(trimmed);
    if (Array.isArray(parsed)) {
      const certificates = parsed
        .map((entry) => (typeof entry === "string" ? entry.trim() : ""))
        .filter(Boolean);
      return certificates.length > 0 ? certificates : [""];
    }
  } catch {
    // Fall back to delimiter parsing for .env-friendly values.
  }

  const certificates = trimmed
    .replace(/\\n/g, "\n")
    .split(/[,\n]/)
    .map((certificate) => certificate.trim())
    .filter(Boolean);

  return certificates.length > 0 ? certificates : [""];
}

const verifierKeyJwkPreset = config.public.verifierKeyJwk;
const verifierX5cPreset = config.public.verifierX5c;
const verifierClientIdPreset = runtimeConfigValueToString(
  config.public.verifierClientId,
);

const keyJson = ref(formatJsonPreset(verifierKeyJwkPreset));
const x5cValues = ref(parseX5cPreset(verifierX5cPreset));
const signedRequest = ref(false);
const encryptedResponse = ref(false);
const dcApiMediationRequired = ref(false);
const optionsError = ref<string | null>(null);
const clientIdType = ref<ClientIdType>("x509_hash");
const clientIdInput = ref("");
const x509HashClientIdPreset = ref("");
const isWritingJson = ref(false);
const transactionDataProfiles = useTransactionDataProfiles(
  config.public.verifierBase as string,
);
const transactionDataEnabled = ref(false);
const transactionDataTouched = ref(false);
const selectedTransactionProfileType = ref("");
const transactionDataFieldValues = ref<Record<string, string>>({});

const clientIdOptions = [
  {
    value: "x509_hash",
    label: "x509_hash (auto from first x5c cert)",
    placeholder: "",
  },
  {
    value: "x509_san_dns",
    label: "x509_san_dns",
    placeholder: "verifier.example.com",
  },
  {
    value: "redirect_uri",
    label: "redirect_uri",
    placeholder: "https://verifier.example.com/callback",
  },
  {
    value: "decentralized_identifier",
    label: "decentralized_identifier",
    placeholder: "did:web:verifier.example.com",
  },
  {
    value: "verifier_attestation",
    label: "verifier_attestation",
    placeholder: "Verifier attestation subject",
  },
  {
    value: "pre_registered",
    label: "Pre-registered client ID",
    placeholder: "my-registered-verifier",
  },
] as const;

const selectedClientIdOption = computed(() =>
  clientIdOptions.find((option) => option.value === clientIdType.value)!,
);
const clientIdNeedsInput = computed(() => clientIdType.value !== "x509_hash");
const selectedSwaggerExample = computed(
  () => props.swagger.examples.value[selectedIndex.value] ?? null,
);
const isDcApiExample = computed(() =>
  selectedSwaggerExample.value?.title.toLowerCase().includes("dc_api"),
);

const canSubmit = computed(() => {
  if (!json.value.trim()) return false;
  try {
    JSON.parse(json.value);
    return true;
  } catch {
    return false;
  }
});
const missingRequiredClientId = computed(
  () => clientIdNeedsInput.value && !clientIdInput.value.trim(),
);
const selectedTransactionProfile = computed(
  () =>
    transactionDataProfiles.profiles.value.find(
      (profile) => profile.type === selectedTransactionProfileType.value,
    ) ??
    transactionDataProfiles.profiles.value[0] ??
    null,
);
const transactionDataUnavailable = computed(
  () =>
    transactionDataEnabled.value &&
    (transactionDataProfiles.loading.value ||
      !!transactionDataProfiles.error.value ||
      !selectedTransactionProfile.value),
);
const submitDisabled = computed(
  () =>
    !canSubmit.value ||
    !!optionsError.value ||
    missingRequiredClientId.value ||
    transactionDataUnavailable.value ||
    props.session.loading.value,
);

function readPayload(): Record<string, unknown> {
  const parsed = JSON.parse(json.value || "{}");
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("Request body must be a JSON object.");
  }
  return parsed as Record<string, unknown>;
}

function getCoreFlowObject(
  payload: Record<string, unknown>,
): Record<string, unknown> {
  const coreFlow = payload.core_flow;
  if (coreFlow && typeof coreFlow === "object" && !Array.isArray(coreFlow)) {
    return coreFlow as Record<string, unknown>;
  }

  payload.core_flow = {};
  return payload.core_flow as Record<string, unknown>;
}

function getOpenIdObject(
  payload: Record<string, unknown>,
): Record<string, unknown> {
  const openid = payload.openid;
  if (openid && typeof openid === "object" && !Array.isArray(openid)) {
    return openid as Record<string, unknown>;
  }

  payload.openid = {};
  return payload.openid as Record<string, unknown>;
}

function addX5cInput() {
  x5cValues.value.push("");
}

function removeX5cInput(index: number) {
  x5cValues.value.splice(index, 1);
  if (x5cValues.value.length === 0) x5cValues.value.push("");
}

async function buildClientId(x5c: string[]): Promise<string | null> {
  if (clientIdType.value === "x509_hash") {
    return x5c.length > 0
      ? await computeX509HashClientId(x5c[0]!)
      : x509HashClientIdPreset.value || null;
  }

  const value = clientIdInput.value.trim();
  if (!value)
    throw new Error(`${selectedClientIdOption.value.label} requires a value.`);

  return clientIdType.value === "pre_registered"
    ? value
    : `${clientIdType.value}:${value}`;
}

function applyClientIdPreset(clientId: string) {
  const trimmed = clientId.trim();
  if (!trimmed) return;

  const prefixedOption = clientIdOptions
    .filter((option) => option.value !== "pre_registered")
    .find((option) => trimmed.startsWith(`${option.value}:`));

  if (prefixedOption) {
    clientIdType.value = prefixedOption.value;
    if (prefixedOption.value === "x509_hash") {
      x509HashClientIdPreset.value = trimmed;
      return;
    }

    clientIdInput.value = trimmed.slice(prefixedOption.value.length + 1);
    return;
  }

  clientIdType.value = "pre_registered";
  clientIdInput.value = trimmed;
}

applyClientIdPreset(verifierClientIdPreset);

function getDcqlCredentialObjects(
  coreFlow: Record<string, unknown>,
): Array<Record<string, unknown>> {
  const dcqlQuery = coreFlow.dcql_query;
  if (!dcqlQuery || typeof dcqlQuery !== "object" || Array.isArray(dcqlQuery)) {
    return [];
  }

  const credentials = (dcqlQuery as Record<string, unknown>).credentials;
  return Array.isArray(credentials) ? credentials.filter(isRecord) : [];
}

function getDcqlCredentialIds(coreFlow: Record<string, unknown>): string[] {
  return getDcqlCredentialObjects(coreFlow)
    .map((credential) => credential.id)
    .filter(
      (id): id is string => typeof id === "string" && id.trim().length > 0,
    );
}

function formatTransactionFieldLabel(field: string) {
  return field
    .replace(/_/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function handleTransactionDataToggle() {
  transactionDataTouched.value = true;
}

function applyTransactionDataOverrides(
  payload: Record<string, unknown>,
  coreFlow: Record<string, unknown>,
) {
  if (!transactionDataEnabled.value) {
    if (transactionDataTouched.value) {
      const openid = payload.openid;
      if (openid && typeof openid === "object" && !Array.isArray(openid)) {
        delete (openid as Record<string, unknown>).transactionData;
        if (Object.keys(openid as Record<string, unknown>).length === 0) {
          delete payload.openid;
        }
      }
    }
    return true;
  }

  const selectedProfile = selectedTransactionProfile.value;
  if (!selectedProfile) {
    optionsError.value = "Select a transaction data profile.";
    return false;
  }

  const credentialIds = getDcqlCredentialIds(coreFlow);
  if (credentialIds.length === 0) {
    optionsError.value =
      "Transaction data requires at least one DCQL credential with an id.";
    return false;
  }

  getDcqlCredentialObjects(coreFlow)
    .filter((credential) => credentialIds.includes(String(credential.id)))
    .forEach((credential) => {
      credential.require_cryptographic_holder_binding = true;
    });

  const openid = getOpenIdObject(payload);
  openid.transactionData = [
    {
      type: selectedProfile.type,
      credential_ids: credentialIds,
      transaction_data_hashes_alg: ["sha-256"],
      require_cryptographic_holder_binding: true,
      ...Object.fromEntries(
        selectedProfile.fields.map((field) => [
          field,
          transactionDataFieldValues.value[field] ?? "",
        ]),
      ),
    },
  ];

  return true;
}

async function applySecurityOverridesToJson() {
  optionsError.value = null;

  let payload: Record<string, unknown>;
  try {
    payload = readPayload();
  } catch (e) {
    optionsError.value =
      e instanceof Error ? e.message : "Request body must be valid JSON.";
    return null;
  }

  const coreFlow = getCoreFlowObject(payload);
  coreFlow.signed_request = signedRequest.value;
  coreFlow.encrypted_response = encryptedResponse.value;

  if (keyJson.value.trim()) {
    try {
      coreFlow.key = parseJwkKey(keyJson.value);
    } catch (e) {
      optionsError.value =
        e instanceof Error ? e.message : "Key must be valid JSON.";
      return null;
    }
  } else {
    delete coreFlow.key;
  }

  try {
    const x5c = parseVerifierX5c(x5cValues.value);
    if (x5c.length > 0) {
      coreFlow.x5c = x5c;
    } else {
      delete coreFlow.x5c;
    }
    const clientId = await buildClientId(x5c);
    if (clientId) {
      coreFlow.clientId = clientId;
    } else {
      delete coreFlow.clientId;
    }
  } catch (e) {
    optionsError.value =
      e instanceof Error ? e.message : "Invalid x5c certificate or clientId.";
    return null;
  }

  if (!applyTransactionDataOverrides(payload, coreFlow)) {
    return null;
  }

  isWritingJson.value = true;
  json.value = JSON.stringify(payload, null, 2);
  return payload;
}

watch(
  [
    keyJson,
    x5cValues,
    signedRequest,
    encryptedResponse,
    clientIdType,
    clientIdInput,
    transactionDataEnabled,
    selectedTransactionProfileType,
    transactionDataFieldValues,
  ],
  () => {
    void applySecurityOverridesToJson();
  },
  { deep: true },
);

watch(selectedIndex, () =>
  nextTick(() => {
    void applySecurityOverridesToJson();
  }),
);

watch(json, () => {
  if (isWritingJson.value) {
    isWritingJson.value = false;
    return;
  }

  nextTick(() => {
    void applySecurityOverridesToJson();
  });
});

watch(
  transactionDataProfiles.profiles,
  (profiles) => {
    if (!selectedTransactionProfileType.value && profiles[0]) {
      selectedTransactionProfileType.value = profiles[0].type;
    }
  },
  { immediate: true },
);

watch(selectedTransactionProfile, (profile) => {
  if (!profile) {
    transactionDataFieldValues.value = {};
    return;
  }

  transactionDataFieldValues.value = Object.fromEntries(
    profile.fields.map((field) => [
      field,
      transactionDataFieldValues.value[field] ?? "",
    ]),
  );
});

onMounted(() => {
  void transactionDataProfiles.load();
});

async function submit() {
  const payload = await applySecurityOverridesToJson();
  if (!payload) return;

  if (isDcApiExample.value) {
    await props.session.createDcApiSession(
      payload,
      dcApiMediationRequired.value,
    );
  } else {
    await props.session.createSession(payload);
  }
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

    <a
      href="https://dcql.walt.id"
      target="_blank"
      rel="noopener noreferrer"
      class="-mt-2 text-sm font-medium text-blue-600 hover:text-blue-700 hover:underline"
    >
      Learn more about DCQL
    </a>

    <details class="group rounded-xl border border-[--color-border] bg-white">
      <summary class="cursor-pointer list-none p-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <span class="text-base font-semibold">Transaction data</span>
            <span class="block text-sm text-[--color-text-muted] mt-1">
              Add OpenID4VP transaction data to the request and bind it to the
              DCQL credential ids in the payload.
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
        <label class="inline-flex items-center gap-2 text-sm font-medium">
          <input
            v-model="transactionDataEnabled"
            type="checkbox"
            @change="handleTransactionDataToggle"
          />
          Enable transaction data
        </label>

        <div
          v-if="transactionDataEnabled"
          class="grid gap-4 rounded-lg border border-[--color-border] bg-slate-50 p-3"
        >
          <div
            v-if="transactionDataProfiles.loading.value"
            class="text-sm text-[--color-text-muted]"
          >
            Loading transaction data profiles...
          </div>
          <p
            v-else-if="transactionDataProfiles.error.value"
            class="text-sm text-red-600"
          >
            {{ transactionDataProfiles.error.value }}
          </p>
          <p
            v-else-if="transactionDataProfiles.profiles.value.length === 0"
            class="text-sm text-[--color-text-muted]"
          >
            No transaction data profiles are configured on the verifier.
          </p>

          <template v-else>
            <div>
              <label class="form-label">Profile</label>
              <select
                v-model="selectedTransactionProfileType"
                class="form-select"
              >
                <option
                  v-for="profile in transactionDataProfiles.profiles.value"
                  :key="profile.type"
                  :value="profile.type"
                >
                  {{ profile.displayName }}
                </option>
              </select>
              <p class="mt-1 text-xs text-[--color-text-muted]">
                The generated <code>credential_ids</code> are taken from
                <code>core_flow.dcql_query.credentials[].id</code>.
              </p>
            </div>

            <div
              v-if="selectedTransactionProfile"
              class="grid sm:grid-cols-2 gap-3"
            >
              <label
                v-for="field in selectedTransactionProfile.fields"
                :key="field"
                class="grid gap-1"
              >
                <span class="form-label !mb-0">
                  {{ formatTransactionFieldLabel(field) }}
                </span>
                <input
                  v-model="transactionDataFieldValues[field]"
                  class="form-input"
                  :name="`transaction-${field}`"
                />
              </label>
            </div>
          </template>
        </div>
      </div>
    </details>

    <details class="group rounded-xl border border-[--color-border] bg-white">
      <summary class="cursor-pointer list-none p-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <span class="text-base font-semibold"
              >Request security options</span
            >
            <span class="block text-sm text-[--color-text-muted] mt-1">
              These controls override the selected request payload before the
              verification session is created.
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
        <div
          v-if="isDcApiExample"
          class="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900"
        >
          <p class="font-medium">Digital Credentials API flow</p>
          <p class="mt-1">
            This example will create a verifier session, fetch the browser API
            request, call <code>navigator.credentials.get</code>, post the
            wallet response, and poll the verifier result.
          </p>
          <label class="mt-3 inline-flex items-center gap-2 font-medium">
            <input v-model="dcApiMediationRequired" type="checkbox" />
            mediation: required
          </label>
        </div>

        <div class="grid gap-3">
          <div>
            <label class="form-label">Client ID type</label>
            <select v-model="clientIdType" class="form-select">
              <option
                v-for="option in clientIdOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </div>

          <div v-if="clientIdNeedsInput">
            <label class="form-label">Client ID value</label>
            <input
              v-model="clientIdInput"
              class="form-input"
              :placeholder="selectedClientIdOption.placeholder"
            />
            <p class="text-xs text-[--color-text-muted] mt-1">
              The selected prefix will be added automatically in
              <code>core_flow.clientId</code>.
            </p>
          </div>

          <p v-else class="text-xs text-[--color-text-muted]">
            The <code>x509_hash</code> client ID is generated automatically from
            the first x5c certificate.
          </p>
        </div>

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
          <div v-for="(_, index) in x5cValues" :key="index" class="flex gap-2">
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
          <button
            type="button"
            class="btn btn-secondary justify-self-start"
            @click="addX5cInput"
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
        Create Verification Session
      </button>
      <span v-if="session.error.value" class="text-sm text-red-600">{{
        session.error.value
      }}</span>
    </div>
  </div>
</template>
