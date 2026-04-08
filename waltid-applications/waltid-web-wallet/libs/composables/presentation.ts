import {encodeDisclosure} from "./disclosures.ts";
import {useCurrentWallet} from "./accountWallet.ts";
import {computed, type Ref, ref, watch} from "vue";
import {decodeRequest} from "./siop-requests.ts";
import {navigateTo} from "nuxt/app";

type MatchedCredential = {
  id: string;
  document: string;
  parsedDocument?: string;
  disclosures?: string;
};

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
  const presentationUrl = new URL(resolvedRequest as string);
  const presentationParams = presentationUrl.searchParams;
  const isOpenId4Vp = presentationParams.has("dcql_query");

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
  const requestForWalletApi = isOpenId4Vp ? (resolvedRequest as string) : originalRequest;
  const matchedCredentials = await fetchMatchedCredentials(
    currentWallet.value,
    requestForWalletApi,
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
      presentationRequest: isOpenId4Vp ? requestForWalletApi : resolvedRequest,
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
