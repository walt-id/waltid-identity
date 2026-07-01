export interface IssuerProfile {
  profileId: string
  name?: string
  credentialConfigurationId?: string
  [key: string]: unknown
}

function getProfileId(raw: Record<string, unknown>, fallback: string): string {
  const id = raw.profileId ?? raw.id
  return typeof id === 'string' && id.length > 0 ? id : fallback
}

export function useProfiles(issuerBase: string) {
  const profiles = ref<IssuerProfile[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Cache of fetched single-profile detail JSON, keyed by profileId.
  const details = ref<Record<string, unknown>>({})
  const detailLoading = ref(false)
  const detailError = ref<string | null>(null)

  const base = issuerBase.replace(/\/+$/, '')

  async function load() {
    loading.value = true
    error.value = null
    try {
      const res = await fetch(`${base}/issuer2/profiles`, { headers: { accept: 'application/json' } })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      const list = Array.isArray(data) ? data : []
      profiles.value = list.map((entry, i) => {
        const obj = (entry ?? {}) as Record<string, unknown>
        return { ...obj, profileId: getProfileId(obj, `profile-${i}`) } as IssuerProfile
      })
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load profiles'
      profiles.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadDetail(profileId: string) {
    if (!profileId) return
    if (details.value[profileId]) return
    detailLoading.value = true
    detailError.value = null
    try {
      const res = await fetch(`${base}/issuer2/profiles/${encodeURIComponent(profileId)}`, {
        headers: { accept: 'application/json' },
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      details.value = { ...details.value, [profileId]: await res.json() }
    } catch (e) {
      detailError.value = e instanceof Error ? e.message : 'Failed to load profile'
    } finally {
      detailLoading.value = false
    }
  }

  return { profiles, loading, error, load, details, detailLoading, detailError, loadDetail }
}
