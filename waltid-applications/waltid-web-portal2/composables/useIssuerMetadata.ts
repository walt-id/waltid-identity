export interface IssuerCredentialDisplay {
  name?: string
  locale?: string
  description?: string
  background_color?: string
  text_color?: string
  background_image?: { uri?: string }
  logo?: { uri?: string; alt_text?: string }
}

export interface IssuerCredentialClaimDisplay {
  name?: string
  locale?: string
}

export interface IssuerCredentialClaim {
  path?: string[]
  mandatory?: boolean
  display?: IssuerCredentialClaimDisplay[]
}

export interface IssuerCredentialConfiguration {
  format?: string
  scope?: string
  doctype?: string
  vct?: string
  credential_definition?: {
    type?: string[]
    [key: string]: unknown
  }
  credential_metadata?: {
    display?: IssuerCredentialDisplay[]
    claims?: IssuerCredentialClaim[]
  }
  [key: string]: unknown
}

export interface CredentialIssuerMetadata {
  credential_issuer?: string
  credential_configurations_supported?: Record<string, IssuerCredentialConfiguration>
  [key: string]: unknown
}

export function useIssuerMetadata(issuerBase: string) {
  const metadata = ref<CredentialIssuerMetadata | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const base = issuerBase.replace(/\/+$/, "")

  const configurations = computed(
    () => metadata.value?.credential_configurations_supported ?? {},
  )

  async function load() {
    loading.value = true
    error.value = null
    try {
      const res = await fetch(
        `${base}/.well-known/openid-credential-issuer/openid4vci`,
        { headers: { accept: "application/json" } },
      )
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      metadata.value = (await res.json()) as CredentialIssuerMetadata
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to load issuer metadata"
      metadata.value = null
    } finally {
      loading.value = false
    }
  }

  function configurationFor(credentialConfigurationId?: string) {
    if (!credentialConfigurationId) return undefined
    return configurations.value[credentialConfigurationId]
  }

  return { metadata, configurations, loading, error, load, configurationFor }
}
