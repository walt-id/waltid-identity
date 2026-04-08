import {encodeDisclosure} from "./disclosures.ts";
import {useCurrentWallet} from "./accountWallet.ts";
import {computed, type Ref, ref, watch} from "vue";
import {decodeRequest} from "./siop-requests.ts";
import {navigateTo} from "nuxt/app";
import {parseJwt} from "@waltid-web-wallet/utils/jwt.ts";

type PresentationTransactionDataItem = {
  type: string;
  credential_ids: string[];
  transaction_data_hashes_alg?: string[];
  require_cryptographic_holder_binding?: boolean;
  [key: string]: unknown;
};

type MatchedCredential = {
  id: string;
  document: string;
  parsedDocument?: Record<string, unknown>;
  disclosures?: string;
  format?: string;
};

function parseStringArrayParameter(value: string | null): string[] {
  if (!value) {
    return [];
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(value);
  } catch {
    throw new Error("Invalid transaction_data: expected a JSON array of strings.");
  }

  if (!Array.isArray(parsed) || !parsed.every((entry): entry is string => typeof entry === "string")) {
    throw new Error("Invalid transaction_data: expected a JSON array of strings.");
  }

  return parsed;
}

function decodeBase64UrlJson<T>(value: string): T {
  try {
    const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
    const padding = "=".repeat((4 - (base64.length % 4)) % 4);
    const decoded = window.atob(`${base64}${padding}`);
    const utf8 = decodeURIComponent(
      Array.from(decoded)
        .map((character) => `%${character.charCodeAt(0).toString(16).padStart(2, "0")}`)
        .join(""),
    );
    return JSON.parse(utf8) as T;
  } catch {
    throw new Error("Invalid transaction_data: malformed base64url or JSON.");
  }
}

function isPresentationTransactionDataItem(value: unknown): value is PresentationTransactionDataItem {
  return (
    typeof value === "object" &&
    value !== null &&
    typeof (value as PresentationTransactionDataItem).type === "string" &&
    Array.isArray((value as PresentationTransactionDataItem).credential_ids) &&
    (value as PresentationTransactionDataItem).credential_ids.every((credentialId) => typeof credentialId === "string")
  );
}

const TRANSACTION_DATA_KNOWN_FIELDS = [
  "type",
  "credential_ids",
  "transaction_data_hashes_alg",
  "require_cryptographic_holder_binding",
];

export function transactionDataEntries(transactionDataItem: Record<string, unknown>) {
  return Object.entries(transactionDataItem).filter(
    ([field]) => !TRANSACTION_DATA_KNOWN_FIELDS.includes(field),
  );
}

export function formatTransactionDataField(field: string) {
  return field.replace(/_/g, " ");
}

export function formatTransactionDataValue(value: unknown) {
  return typeof value === "string" ? value : JSON.stringify(value);
}

export async function usePresentation(query: any) {
  const index = ref(0);
  const failed = ref(false);
  const failMessage = ref("Unknown error occurred.");

  const currentWallet = useCurrentWallet();
  const originalRequest = decodeRequest(query.request as string);

  async function resolvePresentationRequest(request: string) {
    try {
      const response = await $fetch(
        `/wallet-api/wallet/${currentWallet.value}/exchange/resolvePresentationRequest`,
        {
          method: "POST",
          body: request,
        },
      );
      return response;
    } catch (e) {
      failed.value = true;
      throw e;
    }
  }

  const resolvedRequest = await resolvePresentationRequest(originalRequest);
  const presentationParams = extractPresentationParams(resolvedRequest as string);
  const isOpenId4Vp = presentationParams.has("dcql_query");
  let transactionDataItems: PresentationTransactionDataItem[];
  try {
    transactionDataItems = parseStringArrayParameter(
      presentationParams.get("transaction_data"),
    ).map((encoded) => {
      const item = decodeBase64UrlJson<unknown>(encoded);
      if (!isPresentationTransactionDataItem(item)) {
        throw new Error("Invalid transaction_data: each item must define type and credential_ids.");
      }
      return item;
    });
  } catch (error) {
    failed.value = true;
    failMessage.value = error instanceof Error ? error.message : "Invalid transaction data in presentation request.";
    throw error;
  }

  const verifierHost = new URL(
    presentationParams.get("response_uri") ??
      presentationParams.get("redirect_uri") ??
      "",
  ).host;
  const presentationRequestPayload = (presentationParams.get(
    "presentation_definition",
  ) ?? presentationParams.get("dcql_query")) as string;
  if (!presentationRequestPayload) {
    failed.value = true;
    failMessage.value = isOpenId4Vp
      ? "Presentation request is missing dcql_query."
      : "Presentation request is missing presentation_definition.";
    throw new Error(failMessage.value);
  }
  const requestForCredentialMatching = isOpenId4Vp
    ? (resolvedRequest as string)
    : originalRequest;
  const matchedCredentials = await fetchMatchedCredentials(
    currentWallet.value,
    requestForCredentialMatching,
    presentationRequestPayload,
    isOpenId4Vp,
  );

  const selection = ref<{ [key: string]: boolean }>({});
  const selectedCredentialIds = computed(() =>
    Object.entries(selection.value)
      .filter((it) => it[1])
      .map((it) => it[0]),
  );
  for (let credential of matchedCredentials) {
    selection.value[credential.id] = true;
  }

  const disclosures: Ref<{ [key: string]: any[] }> = ref({});
  const encodedDisclosures = computed(() => {
    if (JSON.stringify(disclosures.value) === "{}") return null;

    const m: { [key: string]: any[] } = {};
    for (let credId in disclosures.value) {
      if (m[credId] === undefined) {
        m[credId] = [];
      }

      for (let disclosure of disclosures.value[credId]) {
        m[credId].push(encodeDisclosure(disclosure));
      }
    }

    return m;
  });

  function addDisclosure(credentialId: string, disclosure: string) {
    if (disclosures.value[credentialId] === undefined) {
      disclosures.value[credentialId] = [];
    }
    disclosures.value[credentialId].push(disclosure);
  }

  function removeDisclosure(credentialId: string, disclosure: string) {
    disclosures.value[credentialId] = disclosures.value[credentialId].filter(
      (elem) => elem[0] != disclosure[0],
    );
  }

  const disclosureModalState: Ref<{ [key: string]: boolean }> = ref({});

  for (let credential of matchedCredentials) {
    disclosureModalState.value[credential.id] = false;
  }
  if (matchedCredentials[index.value]) {
    disclosureModalState.value[matchedCredentials[index.value].id] = true;
  }

  function toggleDisclosure(credentialId: string) {
    disclosureModalState.value[credentialId] =
      !disclosureModalState.value[credentialId];
  }

  // Disable all disclosure modals when switching between credentials and set the current one to active
  watch(index, () => {
    for (let credential of matchedCredentials) {
      disclosureModalState.value[credential.id] = false;
    }
    disclosureModalState.value[matchedCredentials[index.value].id] = true;
  });

  async function acceptPresentation() {
    const req = {
      //did: String, // todo: choose DID of shared credential // for now wallet-api chooses the default wallet did
      presentationRequest: resolvedRequest,
      selectedCredentials: selectedCredentialIds.value,
      disclosures: isOpenId4Vp ? null : encodedDisclosures.value,
    };

    const response = await fetch(
      `/wallet-api/wallet/${currentWallet.value}/exchange/usePresentationRequest`,
      {
        method: "POST",
        body: JSON.stringify(req),
        redirect: "manual",
        headers: {
          "Content-Type": "application/json",
        },
      },
    );

    if (response.ok) {
      const parsedResponse: { redirectUri: string } = await response.json();
      if (parsedResponse.redirectUri) {
        navigateTo(parsedResponse.redirectUri, {
          external: true,
        });
      } else {
        window.alert("Presentation successful, no redirect URL supplied.");
        navigateTo(`/wallet/${currentWallet.value}`, {
          external: true,
        });
      }
    } else {
      failed.value = true;
      const error: {
        message: string;
        redirectUri: string | null | undefined;
        errorMessage: string;
      } = await response.json();
      failMessage.value = error.message;

      console.log("Error response: " + JSON.stringify(error));
      window.alert(error.errorMessage);

      if (error.redirectUri != null) {
        navigateTo(error.redirectUri as string, {
          external: true,
        });
      }
    }
  }

  return {
    currentWallet,
    verifierHost,
    transactionDataItems,
    requestPayload: presentationRequestPayload,
    matchedCredentials,
    selectedCredentialIds,
    disclosures,
    selection,
    index,
    disclosureModalState,
    toggleDisclosure,
    addDisclosure,
    removeDisclosure,
    acceptPresentation,
    failed,
    failMessage,
  };
}

function extractPresentationParams(request: string): URLSearchParams {
  const requestUrl = new URL(request);
  const requestObject = requestUrl.searchParams.get("request");
  if (!requestObject) {
    return requestUrl.searchParams;
  }

  const payload = parseRequestObjectPayload(requestObject);
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(payload)) {
    if (value == null) continue;
    params.set(key, typeof value === "string" ? value : JSON.stringify(value));
  }
  return params;
}

function parseRequestObjectPayload(
  requestObject: string,
): Record<string, unknown> {
  try {
    const payload = parseJwt(requestObject);
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
      throw new Error("Request object payload is not a JSON object.");
    }
    return payload as Record<string, unknown>;
  } catch {
    throw new Error("Malformed request object in presentation request.");
  }
}

async function fetchMatchedCredentials(
  walletId: string,
  originalRequest: string,
  presentationRequestPayload: string,
  isOpenId4Vp: boolean,
) {
  const path = isOpenId4Vp
    ? "matchCredentialsForPresentationRequest"
    : "matchCredentialsForPresentationDefinition";
  const body = isOpenId4Vp ? originalRequest : presentationRequestPayload;

  return $fetch<Array<MatchedCredential>>(
    `/wallet-api/wallet/${walletId}/exchange/${path}`,
    {
      method: "POST",
      body,
    },
  );
}
