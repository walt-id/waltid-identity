export interface VerifierSessionResult {
  sessionId: string
  authorizationRequestUrl: string
}

export function useVerifierSession(verifierBase: string) {
  const result = ref<VerifierSessionResult | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const sse = useSSE()

  async function createSession(payload: unknown) {
    loading.value = true
    error.value = null
    result.value = null
    sse.close()

    try {
      const res = await fetch(`${verifierBase}/verification-session/create`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', accept: 'application/json' },
        body: JSON.stringify(payload),
      })
      if (!res.ok) {
        const body = await res.text()
        throw new Error(`HTTP ${res.status}: ${body}`)
      }

      const data = await res.json()
      const sessionId: string = data.sessionId ?? data.id
      const authorizationRequestUrl: string =
        data.bootstrapAuthorizationRequestUrl ?? data.fullAuthorizationRequestUrl ?? data.authorizationRequestUrl ?? data.request_uri

      result.value = { sessionId, authorizationRequestUrl }

      sse.open(`${verifierBase}/verification-session/${sessionId}/events`)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
    } finally {
      loading.value = false
    }
  }

  return { result, loading, error, createSession, sse }
}
