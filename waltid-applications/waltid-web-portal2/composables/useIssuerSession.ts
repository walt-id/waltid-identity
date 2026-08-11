import {
  enrichCredentialOfferForDcApi,
  getDcApiIssuanceSupport,
  invokeDigitalCredentialsCreate,
  parseCredentialOfferFromUrl,
} from "~/utils/dcApiIssuance";

export interface IssuerSessionResult {
  offerId: string;
  sessionId: string;
  credentialOffer: string;
  txCodeValue?: string;
  flowType: "qr" | "dc_api";
}

type CredentialOfferCreateResponse = {
  offerId?: string;
  sessionId?: string;
  credentialOffer?: string;
  txCodeValue?: string;
};

export function useIssuerSession(issuerBase: string) {
  const result = ref<IssuerSessionResult | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  const sse = useSSE();

  async function createOffer(payload: unknown) {
    loading.value = true;
    error.value = null;
    result.value = null;
    sse.close();

    try {
      const data = await postCredentialOffer(payload);
      const { offerId, sessionId, credentialOffer, txCodeValue } =
        normalizeCreateResponse(data);

      result.value = {
        offerId,
        sessionId,
        credentialOffer,
        txCodeValue,
        flowType: "qr",
      };

      sse.open(`${issuerBase}/issuer2/sessions/${sessionId}/events`);
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Unknown error";
    } finally {
      loading.value = false;
    }
  }

  async function createDcApiOffer(
    payload: unknown,
    mediationRequired = false,
  ) {
    loading.value = true;
    error.value = null;
    result.value = null;
    sse.reset();

    try {
      const support = getDcApiIssuanceSupport();
      if (!support.supported) {
        throw new Error(
          support.reason ?? "Digital Credentials API issuance is unavailable.",
        );
      }

      const createPayload =
        payload != null && typeof payload === "object" && !Array.isArray(payload)
          ? { ...(payload as Record<string, unknown>), valueMode: "BY_VALUE" }
          : { valueMode: "BY_VALUE" };

      const data = await postCredentialOffer(createPayload);
      const { offerId, sessionId, credentialOffer, txCodeValue } =
        normalizeCreateResponse(data);

      result.value = {
        offerId,
        sessionId,
        credentialOffer,
        txCodeValue,
        flowType: "dc_api",
      };

      // Outcome is driven by issuer OpenID4VCI SSE only. Browser create() handoff
      // often fails with NetworkError even when issuance succeeded.
      sse.open(`${issuerBase}/issuer2/sessions/${sessionId}/events`);

      const offer = parseCredentialOfferFromUrl(credentialOffer);
      const [credentialIssuerMetadata, authorizationServerMetadata] =
        await Promise.all([
          fetchJson<unknown>(
            `${issuerBase}/.well-known/openid-credential-issuer/openid4vci`,
          ),
          fetchJson<unknown>(
            `${issuerBase}/.well-known/oauth-authorization-server/openid4vci`,
          ),
        ]);

      const enrichedOffer = enrichCredentialOfferForDcApi(
        offer,
        credentialIssuerMetadata,
        authorizationServerMetadata,
      );

      // Kick off wallet engagement (Chrome proximity QR / local wallet picker),
      // then stop waiting on create() — do not log or surface handoff errors.
      void invokeDigitalCredentialsCreate(
        enrichedOffer,
        mediationRequired,
      ).catch(() => undefined);
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Unknown error";
    } finally {
      loading.value = false;
    }
  }

  async function postCredentialOffer(
    payload: unknown,
  ): Promise<CredentialOfferCreateResponse> {
    const res = await fetch(`${issuerBase}/issuer2/credential-offers`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        accept: "application/json",
      },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const body = await res.text();
      throw new Error(`HTTP ${res.status}: ${body}`);
    }
    return (await res.json()) as CredentialOfferCreateResponse;
  }

  function clear() {
    result.value = null;
    error.value = null;
    loading.value = false;
    sse.reset();
  }

  return {
    result,
    loading,
    error,
    createOffer,
    createDcApiOffer,
    clear,
    sse,
  };
}

function normalizeCreateResponse(data: CredentialOfferCreateResponse) {
  const offerId = data.offerId ?? data.sessionId;
  const sessionId = data.sessionId ?? data.offerId;
  const credentialOffer = data.credentialOffer;

  if (!offerId || !sessionId) {
    throw new Error("No offerId/sessionId returned from create endpoint");
  }
  if (!credentialOffer) {
    throw new Error("No credentialOffer returned from create endpoint");
  }

  return {
    offerId,
    sessionId,
    credentialOffer,
    txCodeValue: data.txCodeValue,
  };
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url, {
    method: "GET",
    headers: { accept: "application/json" },
  });
  if (!response.ok) {
    const errorText = await response.text().catch(() => "");
    throw new Error(`HTTP ${response.status}: ${errorText}`);
  }

  const text = await response.text();
  if (!text) return {} as T;

  try {
    return JSON.parse(text) as T;
  } catch {
    return { raw: text } as T;
  }
}
