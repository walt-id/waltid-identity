export interface SwaggerExample {
  title: string
  payload: unknown
}

function getCandidateUrls(base: string): string[] {
  const normalized = base.replace(/\/+$/, '')
  const urls = [`${normalized}/api.json`]

  try {
    const parsed = new URL(normalized)
    const originUrl = `${parsed.origin}/api.json`
    if (!urls.includes(originUrl)) urls.push(originUrl)
  } catch {
    // relative or malformed base — skip origin variant
  }

  return urls
}

function normalizePayload(raw: unknown): unknown {
  if (typeof raw === 'string') {
    try { return JSON.parse(raw) } catch { return raw }
  }
  return raw
}

function extractExamples(spec: unknown, apiPath: string): SwaggerExample[] {
  const root = spec as Record<string, unknown>
  const paths = root?.paths as Record<string, unknown> | undefined
  if (!paths) return []

  const pathEntry = Object.entries(paths).find(([key]) => key.endsWith(apiPath))
  if (!pathEntry) return []

  const post = (pathEntry[1] as Record<string, unknown>)?.post as Record<string, unknown> | undefined
  const examples = (
    (post?.requestBody as Record<string, unknown>)?.content as Record<string, unknown>
  )?.['application/json'] as Record<string, unknown> | undefined

  const examplesObj = examples?.examples as Record<string, unknown> | undefined
  if (!examplesObj) return []

  return Object.entries(examplesObj).map(([title, entry]) => {
    const value = (entry as Record<string, unknown>)?.value
    return { title, payload: normalizePayload(value) }
  })
}

export function useSwaggerExamples(base: string, apiPath: string) {
  const examples = ref<SwaggerExample[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function load() {
    loading.value = true
    error.value = null
    const urls = getCandidateUrls(base)
    let lastErr: unknown

    for (const url of urls) {
      try {
        const res = await fetch(url, { headers: { accept: 'application/json' } })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const spec = await res.json()
        examples.value = extractExamples(spec, apiPath)
        loading.value = false
        return
      } catch (e) {
        lastErr = e
      }
    }

    error.value = lastErr instanceof Error ? lastErr.message : 'Failed to load OpenAPI spec'
    loading.value = false
  }

  return { examples, loading, error, load }
}
