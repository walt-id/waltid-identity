export interface IssuerSessionResult {
  offerId: string;
  sessionId: string;
  credentialOffer: string;
  txCodeValue?: string;
}

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

      const data = await res.json();
      // offerId and sessionId are the same in issuer2 (the offer IS the session)
      const offerId: string = data.offerId ?? data.sessionId;
      const sessionId: string = data.sessionId ?? data.offerId;

      result.value = {
        offerId,
        sessionId,
        credentialOffer: data.credentialOffer,
        txCodeValue: data.txCodeValue,
      };

      sse.open(`${issuerBase}/issuer2/sessions/${sessionId}/events`);
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Unknown error";
    } finally {
      loading.value = false;
    }
  }

  function clear() {
    result.value = null;
    error.value = null;
    loading.value = false;
    sse.reset();
  }

  return { result, loading, error, createOffer, clear, sse };
}
