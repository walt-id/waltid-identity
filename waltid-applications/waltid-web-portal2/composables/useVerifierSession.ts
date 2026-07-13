export interface VerifierSessionResult {
  sessionId: string;
  authorizationRequestUrl?: string;
  flowType: "qr" | "dc_api";
  finalInfo?: unknown;
}

const DC_API_POLL_INTERVAL_MS = 5_000;
const DC_API_MAX_POLL_ATTEMPTS = 12;

type CreateResponse = {
  sessionId?: string;
  id?: string;
  bootstrapAuthorizationRequestUrl?: string;
  fullAuthorizationRequestUrl?: string;
  authorizationRequestUrl?: string;
  request_uri?: string;
};

type DigitalCredentialsNavigator = Navigator & {
  credentials?: {
    get?: (options: unknown) => Promise<unknown>;
  };
};

export function useVerifierSession(verifierBase: string) {
  const result = ref<VerifierSessionResult | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  const sse = useSSE();

  async function createSession(payload: unknown) {
    loading.value = true;
    error.value = null;
    result.value = null;
    sse.close();

    try {
      const res = await fetch(`${verifierBase}/verification-session/create`, {
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

      const data = await res.json();
      const sessionId: string = data.sessionId ?? data.id;
      const authorizationRequestUrl: string =
        data.bootstrapAuthorizationRequestUrl ??
        data.fullAuthorizationRequestUrl ??
        data.authorizationRequestUrl ??
        data.request_uri;

      result.value = { sessionId, authorizationRequestUrl, flowType: "qr" };

      sse.open(`${verifierBase}/verification-session/${sessionId}/events`);
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Unknown error";
    } finally {
      loading.value = false;
    }
  }

  async function createDcApiSession(
    payload: unknown,
    mediationRequired = false,
  ) {
    loading.value = true;
    error.value = null;
    result.value = null;
    sse.reset();

    try {
      sse.addEvent({ event: "DC_API_FLOW_STARTED", status: "STARTED" });

      const createResponse = await fetchJson<CreateResponse>(
        `${verifierBase}/verification-session/create`,
        {
          method: "POST",
          headers: {
            "content-type": "application/json",
            accept: "application/json",
          },
          body: JSON.stringify(payload),
        },
      );

      const sessionId = createResponse.sessionId ?? createResponse.id;
      if (!sessionId)
        throw new Error("No sessionId returned from create endpoint");

      result.value = { sessionId, flowType: "dc_api" };
      sse.addEvent({ event: "DC_API_SESSION_CREATED", sessionId });

      const dcApiRequest = await fetchJson<unknown>(
        `${verifierBase}/verification-session/${encodeURIComponent(sessionId)}/request`,
        { method: "GET", headers: { accept: "application/json" } },
      );
      sse.addEvent({ event: "DC_API_REQUEST_FETCHED", request: dcApiRequest });

      const dcApiResponse = await invokeDigitalCredentialsApi(
        dcApiRequest,
        mediationRequired,
      );
      sse.addEvent({
        event: "DC_API_WALLET_RESPONSE_RECEIVED",
        response: dcApiResponse,
      });

      await fetchAny(
        `${verifierBase}/verification-session/${encodeURIComponent(sessionId)}/response`,
        {
          method: "POST",
          headers: {
            "content-type": "application/json",
            accept: "application/json",
          },
          body: JSON.stringify(dcApiResponse),
        },
      );
      sse.addEvent({ event: "DC_API_WALLET_RESPONSE_POSTED" });

      const finalInfo = await pollInfo(sessionId);
      result.value = { sessionId, flowType: "dc_api", finalInfo };
      sse.setTerminal("SUCCESSFUL", finalInfo);
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Unknown error";
      sse.setTerminal("FAILED", {
        event: "DC_API_FLOW_FAILED",
        status: "FAILED",
        message: error.value,
      });
    } finally {
      loading.value = false;
    }
  }

  async function pollInfo(sessionId: string): Promise<unknown> {
    for (let attempt = 1; attempt <= DC_API_MAX_POLL_ATTEMPTS; attempt += 1) {
      const info = await fetchJson<unknown>(
        `${verifierBase}/verification-session/${encodeURIComponent(sessionId)}/info`,
        { method: "GET", headers: { accept: "application/json" } },
      );
      const status = getInfoStatus(info);
      sse.addEvent({
        event: "DC_API_INFO_POLL",
        attempt,
        status: status ?? "MISSING",
        info,
      });

      if (status === "SUCCESSFUL") return info;
      if (status === "FAILED")
        throw new Error("Verification failed with status FAILED");

      await sleep(DC_API_POLL_INTERVAL_MS);
    }

    throw new Error(
      `Result not available after ${DC_API_MAX_POLL_ATTEMPTS} polling attempts`,
    );
  }

  return { result, loading, error, createSession, createDcApiSession, sse };
}

async function invokeDigitalCredentialsApi(
  requestPayload: unknown,
  mediationRequired: boolean,
): Promise<unknown> {
  const nav = navigator as DigitalCredentialsNavigator;

  if (typeof nav.credentials?.get !== "function") {
    throw new Error(
      "Digital Credentials API is unavailable in this browser (navigator.credentials.get missing).",
    );
  }

  const digitalPayload =
    requestPayload != null &&
    typeof requestPayload === "object" &&
    Object.prototype.hasOwnProperty.call(requestPayload, "digital")
      ? (requestPayload as { digital: unknown }).digital
      : { requests: [requestPayload] };

  const dcRequestPayload: { mediation?: string; digital: unknown } = {
    digital: digitalPayload,
  };

  if (mediationRequired) dcRequestPayload.mediation = "required";

  const response = await nav.credentials.get(dcRequestPayload);
  if (response == null)
    throw new Error("Digital Credentials API returned empty response");
  return response;
}

async function fetchJson<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetchAny(url, init);
  const text = await response.text();
  if (!text) return {} as T;

  try {
    return JSON.parse(text) as T;
  } catch {
    return { raw: text } as T;
  }
}

async function fetchAny(url: string, init: RequestInit): Promise<Response> {
  const response = await fetch(url, init);
  if (!response.ok) {
    const errorText = await response.text().catch(() => "");
    throw new Error(`HTTP ${response.status}: ${errorText}`);
  }
  return response;
}

function getInfoStatus(info: unknown): string | null {
  if (!info || typeof info !== "object") return null;

  const rootStatus = (info as { status?: unknown }).status;
  if (typeof rootStatus === "string") return rootStatus.toUpperCase();

  const session = (info as { session?: unknown }).session;
  if (!session || typeof session !== "object") return null;

  const status = (session as { status?: unknown }).status;
  return typeof status === "string" ? status.toUpperCase() : null;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
